package com.mi.explorer.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.components.ExifCleanerDialog
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.utils.FileOpener
import com.mi.explorer.utils.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class ImageFilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid HDR"),
    WARM("Warm Sun"),
    COOL("Cyber Cool"),
    BW_NOIR("B&W Noir"),
    SEPIA("Vintage")
}

enum class ImageCropRatio(val label: String, val ratio: Float?) {
    ORIGINAL("Free / Full", null),
    SQUARE("1:1 Square", 1f),
    WIDE_16_9("16:9 Cinema", 16f / 9f),
    STANDARD_4_3("4:3 Classic", 4f / 3f),
    STORY_9_16("9:16 Story", 9f / 16f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.imageViewerState.collectAsStateWithLifecycle()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showExifCleaner by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var isEditStudioOpen by remember { mutableStateOf(false) }
    var editTabIndex by remember { mutableIntStateOf(0) } // 0=Transform/Crop, 1=Filters, 2=Adjust

    // Image Transform & Filter State
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(ImageFilterPreset.ORIGINAL) }
    var selectedCropRatio by remember { mutableStateOf(ImageCropRatio.ORIGINAL) }
    var brightnessAdj by remember { mutableFloatStateOf(0f) }   // -80f..+80f
    var contrastAdj by remember { mutableFloatStateOf(1f) }     // 0.5f..1.8f
    var saturationAdj by remember { mutableFloatStateOf(1f) }   // 0f..2.0f

    // Pinch-to-Zoom & Pan State
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
        if (zoomScale > 1f) {
            panOffset += offsetChange
        } else {
            panOffset = Offset.Zero
        }
    }

    var reloadTrigger by remember { mutableIntStateOf(0) }

    val images = state.imageList
    val pageCount = images.size.coerceAtLeast(1)
    val initialPage = remember(images) {
        state.currentIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount }
    )

    // Sync external index changes with pager (when opening new image or changing from outside)
    LaunchedEffect(state.currentFile?.absolutePath, state.currentIndex) {
        if (state.currentIndex in 0 until pageCount && pagerState.currentPage != state.currentIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(state.currentIndex)
        }
    }

    // Sync pager swipe with viewModel ONLY after page has settled (prevents gesture interruptions)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (images.isNotEmpty() && settled in images.indices && settled != state.currentIndex) {
                viewModel.setImageIndex(settled)
            }
        }
    }

    // Reset edits when switching image
    LaunchedEffect(state.currentFile?.absolutePath) {
        rotationDegrees = 0f
        flipHorizontal = false
        flipVertical = false
        selectedFilter = ImageFilterPreset.ORIGINAL
        selectedCropRatio = ImageCropRatio.ORIGINAL
        brightnessAdj = 0f
        contrastAdj = 1f
        saturationAdj = 1f
        zoomScale = 1f
        panOffset = Offset.Zero
    }

    var bitmap by remember(state.currentFile?.absolutePath, reloadTrigger) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(state.currentFile?.absolutePath, reloadTrigger) {
        val file = state.currentFile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                val sample = if (maxDim > 3072) maxDim / 3072 else 1
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                withContext(Dispatchers.Main) {
                    bitmap = decoded
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    bitmap = null
                }
            }
        }
    }

    val composeColorMatrix = remember(selectedFilter, brightnessAdj, contrastAdj, saturationAdj) {
        buildComposeColorMatrix(selectedFilter, brightnessAdj, contrastAdj, saturationAdj)
    }

    val hasUnsavedEdits = rotationDegrees != 0f ||
        flipHorizontal ||
        flipVertical ||
        selectedFilter != ImageFilterPreset.ORIGINAL ||
        selectedCropRatio != ImageCropRatio.ORIGINAL ||
        brightnessAdj != 0f ||
        contrastAdj != 1f ||
        saturationAdj != 1f

    Scaffold(
        modifier = modifier.testTag("image_viewer_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.currentFile?.name ?: "Photos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.imageList.isNotEmpty()) {
                            val currentBmp = bitmap
                            Text(
                                text = "${state.currentIndex + 1} of ${state.imageList.size}" +
                                    (if (currentBmp != null) " • ${currentBmp.width}×${currentBmp.height}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("image_viewer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isEditStudioOpen = !isEditStudioOpen }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Pro Image Editor",
                            tint = if (isEditStudioOpen || hasUnsavedEdits) MiOrange else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showCompressDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = "Resize & Compress",
                            tint = Color(0xFF3B82F6)
                        )
                    }
                    IconButton(onClick = { showExifCleaner = true }) {
                        Icon(Icons.Default.Security, contentDescription = "EXIF Privacy Cleaner", tint = Color(0xFF10B981))
                    }
                    if (state.currentFile != null) {
                        IconButton(onClick = {
                            FileOpener.shareFile(context, FileItem(state.currentFile!!))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Details")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Pro Image Editor Drawer
                AnimatedVisibility(visible = isEditStudioOpen) {
                    Surface(
                        color = Color(0xFF111827),
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Top Header with Reset & Save Copy
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Transform" to 0, "Filters" to 1, "Adjust" to 2).forEach { (tabTitle, idx) ->
                                        val selected = editTabIndex == idx
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) MiOrange else Color.White.copy(alpha = 0.1f),
                                            modifier = Modifier.clickable { editTabIndex = idx }
                                        ) {
                                            Text(
                                                text = tabTitle,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (hasUnsavedEdits) {
                                        TextButton(
                                            onClick = {
                                                rotationDegrees = 0f
                                                flipHorizontal = false
                                                flipVertical = false
                                                selectedFilter = ImageFilterPreset.ORIGINAL
                                                selectedCropRatio = ImageCropRatio.ORIGINAL
                                                brightnessAdj = 0f
                                                contrastAdj = 1f
                                                saturationAdj = 1f
                                            }
                                        ) {
                                            Text("Reset", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val srcBmp = bitmap ?: return@Button
                                            val curFile = state.currentFile ?: return@Button
                                            scope.launch {
                                                val savedFile = renderAndSaveEditedBitmap(
                                                    sourceBitmap = srcBmp,
                                                    sourceFile = curFile,
                                                    rotationDegrees = rotationDegrees,
                                                    flipH = flipHorizontal,
                                                    flipV = flipVertical,
                                                    cropRatio = selectedCropRatio.ratio,
                                                    filter = selectedFilter,
                                                    brightness = brightnessAdj,
                                                    contrast = contrastAdj,
                                                    saturation = saturationAdj,
                                                    scaleFactor = 1.0f,
                                                    quality = 95
                                                )
                                                if (savedFile != null) {
                                                    viewModel.refreshCurrentDirectory()
                                                    viewModel.showMessage("Saved edited photo: ${savedFile.name}")
                                                    isEditStudioOpen = false
                                                } else {
                                                    viewModel.showMessage("Failed to save edited photo")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            when (editTabIndex) {
                                0 -> {
                                    // Rotate, Flip & Aspect Crop
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        OutlinedButton(
                                            onClick = { rotationDegrees = (rotationDegrees - 90f) % 360f },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                                        ) {
                                            Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left", tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("-90°", color = Color.White, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                                        ) {
                                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right", tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+90°", color = Color.White, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { flipHorizontal = !flipHorizontal },
                                            border = BorderStroke(1.dp, if (flipHorizontal) MiOrange else Color.White.copy(alpha = 0.25f))
                                        ) {
                                            Icon(Icons.Default.Flip, contentDescription = "Flip H", tint = if (flipHorizontal) MiOrange else Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Flip H", color = Color.White, fontSize = 12.sp)
                                        }
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(ImageCropRatio.values().toList()) { crop ->
                                            val active = selectedCropRatio == crop
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (active) MiOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                                border = BorderStroke(1.dp, if (active) MiOrange else Color.Transparent),
                                                modifier = Modifier.clickable { selectedCropRatio = crop }
                                            ) {
                                                Text(
                                                    text = crop.label,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    // Studio Filters
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(ImageFilterPreset.values().toList()) { preset ->
                                            val active = selectedFilter == preset
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (active) MiOrange else Color.White.copy(alpha = 0.1f),
                                                modifier = Modifier.clickable { selectedFilter = preset }
                                            ) {
                                                Text(
                                                    text = preset.label,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    // Brightness, Contrast & Saturation Sliders
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Brightness", fontSize = 12.sp, modifier = Modifier.width(76.dp))
                                            Slider(
                                                value = brightnessAdj,
                                                onValueChange = { brightnessAdj = it },
                                                valueRange = -80f..80f,
                                                colors = SliderDefaults.colors(thumbColor = MiOrange, activeTrackColor = MiOrange),
                                                modifier = Modifier.weight(1f).height(26.dp)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Contrast", fontSize = 12.sp, modifier = Modifier.width(76.dp))
                                            Slider(
                                                value = contrastAdj,
                                                onValueChange = { contrastAdj = it },
                                                valueRange = 0.5f..1.8f,
                                                colors = SliderDefaults.colors(thumbColor = MiOrange, activeTrackColor = MiOrange),
                                                modifier = Modifier.weight(1f).height(26.dp)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Saturation", fontSize = 12.sp, modifier = Modifier.width(76.dp))
                                            Slider(
                                                value = saturationAdj,
                                                onValueChange = { saturationAdj = it },
                                                valueRange = 0f..2.0f,
                                                colors = SliderDefaults.colors(thumbColor = MiOrange, activeTrackColor = MiOrange),
                                                modifier = Modifier.weight(1f).height(26.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Horizontal Thumbnail Filmstrip when Edit Studio is closed
                AnimatedVisibility(visible = !isEditStudioOpen && images.size > 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                    ) {
                        val thumbListState = rememberLazyListState()
                        LaunchedEffect(pagerState.currentPage) {
                            thumbListState.animateScrollToItem((pagerState.currentPage - 2).coerceAtLeast(0))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Photos in collection (${images.size})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Swipe or tap to browse",
                                style = MaterialTheme.typography.labelSmall,
                                color = MiOrange
                            )
                        }

                        LazyRow(
                            state = thumbListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(bottom = 6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(images, key = { index, item -> "thumb_${item.path}_$index" }) { index, item ->
                                val isSelected = index == pagerState.currentPage
                                val thumbBitmap by ThumbnailLoader.rememberThumbnailState(
                                    file = item.file,
                                    category = FileCategory.IMAGE,
                                    targetWidth = 140,
                                    targetHeight = 140
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF3F4F6),
                                    border = if (isSelected) BorderStroke(2.5.dp, MiOrange) else BorderStroke(0.5.dp, Color(0xFFE5E7EB)),
                                    modifier = Modifier
                                        .size(if (isSelected) 50.dp else 44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            scope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                ) {
                                    if (thumbBitmap != null) {
                                        Image(
                                            bitmap = thumbBitmap!!.asImageBitmap(),
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(Color(0xFFE5E7EB)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = zoomScale <= 1.05f,
                key = { page -> images.getOrNull(page)?.path ?: page.toString() },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageItem = images.getOrNull(page)
                val pageFile = pageItem?.file ?: state.currentFile
                val isCurrent = page == pagerState.currentPage

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isCurrent && zoomScale > 1.05f) {
                                Modifier.transformable(state = transformState)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent && bitmap != null) {
                        val cropModifier = selectedCropRatio.ratio?.let { ratio ->
                            Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                        } ?: Modifier.fillMaxSize()

                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = pageFile?.name,
                            colorFilter = ColorFilter.colorMatrix(composeColorMatrix),
                            modifier = cropModifier
                                .graphicsLayer(
                                    scaleX = zoomScale * (if (flipHorizontal) -1f else 1f),
                                    scaleY = zoomScale * (if (flipVertical) -1f else 1f),
                                    rotationZ = rotationDegrees,
                                    translationX = panOffset.x,
                                    translationY = panOffset.y
                                )
                                .pointerInput(isCurrent) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (zoomScale > 1.1f) {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                zoomScale = 2.25f
                                            }
                                        }
                                    )
                                },
                            contentScale = if (selectedCropRatio.ratio != null) ContentScale.Crop else ContentScale.Fit
                        )
                    } else if (pageFile != null) {
                        var pageBmp by remember(pageFile.absolutePath) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(pageFile.absolutePath) {
                            withContext(Dispatchers.IO) {
                                try {
                                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeFile(pageFile.absolutePath, boundsOpts)
                                    val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                                    val sample = if (maxDim > 1920) maxDim / 1920 else 1
                                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                                    val decoded = BitmapFactory.decodeFile(pageFile.absolutePath, decodeOpts)
                                    withContext(Dispatchers.Main) {
                                        pageBmp = decoded
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (pageBmp != null) {
                            Image(
                                bitmap = pageBmp!!.asImageBitmap(),
                                contentDescription = pageFile.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MiOrange, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }

            // Left Navigation Button (Previous Picture)
            if (pagerState.currentPage > 0 && zoomScale <= 1.05f) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Picture",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Right Navigation Button (Next Picture)
            if (pagerState.currentPage < pageCount - 1 && zoomScale <= 1.05f) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Picture",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Floating reset zoom badge when zoomed in
            if (zoomScale > 1.05f) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .clickable {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOutMap,
                            contentDescription = "Reset Zoom",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}% • Double tap to reset",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Smart Image Compressor & Resizer Dialog
    val compressBmp = bitmap
    if (showCompressDialog && state.currentFile != null && compressBmp != null) {
        val currentFile = state.currentFile!!
        val originalBytes = currentFile.length()
        var selectedQuality by remember { mutableIntStateOf(80) }
        var selectedScale by remember { mutableFloatStateOf(0.75f) }

        val estimatedBytes = remember(originalBytes, selectedQuality, selectedScale) {
            (originalBytes * selectedScale * selectedScale * (selectedQuality / 100f) * 0.7f).toLong().coerceAtLeast(4096L)
        }

        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Compress, contentDescription = null, tint = MiOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Image Compressor & Resizer")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Original: ${compressBmp.width}×${compressBmp.height} (${FileItem.formatBytes(originalBytes)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f)
                    ) {
                        val newW = (compressBmp.width * selectedScale).roundToInt()
                        val newH = (compressBmp.height * selectedScale).roundToInt()
                        Text(
                            text = "Estimated Output: ${newW}×${newH} (~${FileItem.formatBytes(estimatedBytes)})",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text("Resolution Scale", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("100%" to 1.0f, "75%" to 0.75f, "50%" to 0.5f).forEach { (label, sc) ->
                            val active = selectedScale == sc
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) MiOrange else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedScale = sc }
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    Text("JPEG Compression Quality ($selectedQuality%)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Slider(
                        value = selectedQuality.toFloat(),
                        onValueChange = { selectedQuality = it.roundToInt() },
                        valueRange = 25f..95f,
                        colors = SliderDefaults.colors(thumbColor = MiOrange, activeTrackColor = MiOrange)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val out = renderAndSaveEditedBitmap(
                                sourceBitmap = compressBmp,
                                sourceFile = currentFile,
                                rotationDegrees = rotationDegrees,
                                flipH = flipHorizontal,
                                flipV = flipVertical,
                                cropRatio = selectedCropRatio.ratio,
                                filter = selectedFilter,
                                brightness = brightnessAdj,
                                contrast = contrastAdj,
                                saturation = saturationAdj,
                                scaleFactor = selectedScale,
                                quality = selectedQuality
                            )
                            showCompressDialog = false
                            if (out != null) {
                                viewModel.refreshCurrentDirectory()
                                viewModel.showMessage("Saved compressed image: ${out.name} (${FileItem.formatBytes(out.length())})")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                ) {
                    Text("Compress & Save Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInfoDialog && state.currentFile != null) {
        val f = state.currentFile!!
        val infoBmp = bitmap
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Photo Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "File: ${f.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Path: ${f.absolutePath}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Size: ${FileItem.formatBytes(f.length())}", style = MaterialTheme.typography.bodyMedium)
                    if (infoBmp != null) {
                        Text(text = "Resolution: ${infoBmp.width} × ${infoBmp.height}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showExifCleaner && state.currentFile != null) {
        ExifCleanerDialog(
            item = FileItem(state.currentFile!!),
            onDismiss = { showExifCleaner = false },
            onCleanSaved = {
                reloadTrigger++
                viewModel.showMessage("Photo metadata stripped successfully!")
            }
        )
    }
}

private fun buildComposeColorMatrix(
    filter: ImageFilterPreset,
    brightness: Float,
    contrast: Float,
    saturation: Float
): ColorMatrix {
    val cm = ColorMatrix()
    val effectiveSat = when (filter) {
        ImageFilterPreset.BW_NOIR -> 0f
        ImageFilterPreset.VIVID -> (saturation * 1.35f).coerceAtMost(2.5f)
        else -> saturation
    }
    cm.setToSaturation(effectiveSat)

    val c = when (filter) {
        ImageFilterPreset.VIVID -> contrast * 1.15f
        ImageFilterPreset.BW_NOIR -> contrast * 1.25f
        else -> contrast
    }
    val b = brightness
    val rScale = when (filter) {
        ImageFilterPreset.WARM -> 1.12f
        ImageFilterPreset.SEPIA -> 1.18f
        else -> 1f
    }
    val bScale = when (filter) {
        ImageFilterPreset.COOL -> 1.18f
        ImageFilterPreset.WARM -> 0.88f
        ImageFilterPreset.SEPIA -> 0.78f
        else -> 1f
    }

    val adjustMatrix = ColorMatrix(
        floatArrayOf(
            c * rScale, 0f, 0f, 0f, b,
            0f, c, 0f, 0f, b,
            0f, 0f, c * bScale, 0f, b,
            0f, 0f, 0f, 1f, 0f
        )
    )
    cm.timesAssign(adjustMatrix)
    return cm
}

private suspend fun renderAndSaveEditedBitmap(
    sourceBitmap: Bitmap,
    sourceFile: File,
    rotationDegrees: Float,
    flipH: Boolean,
    flipV: Boolean,
    cropRatio: Float?,
    filter: ImageFilterPreset,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    scaleFactor: Float,
    quality: Int
): File? = withContext(Dispatchers.IO) {
    try {
        // 1. Apply Rotation & Flip Matrix
        val matrix = Matrix().apply {
            if (flipH || flipV) {
                postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
            }
            if (rotationDegrees != 0f) {
                postRotate(rotationDegrees)
            }
            if (scaleFactor != 1.0f) {
                postScale(scaleFactor, scaleFactor)
            }
        }
        var working = Bitmap.createBitmap(
            sourceBitmap,
            0,
            0,
            sourceBitmap.width,
            sourceBitmap.height,
            matrix,
            true
        )

        // 2. Apply Center Aspect Ratio Crop if requested
        if (cropRatio != null) {
            val w = working.width
            val h = working.height
            val currentRatio = w.toFloat() / h.toFloat()
            if (kotlin.math.abs(currentRatio - cropRatio) > 0.02f) {
                val targetW: Int
                val targetH: Int
                if (currentRatio > cropRatio) {
                    targetH = h
                    targetW = (h * cropRatio).roundToInt().coerceIn(1, w)
                } else {
                    targetW = w
                    targetH = (w / cropRatio).roundToInt().coerceIn(1, h)
                }
                val startX = ((w - targetW) / 2).coerceAtLeast(0)
                val startY = ((h - targetH) / 2).coerceAtLeast(0)
                working = Bitmap.createBitmap(working, startX, startY, targetW, targetH)
            }
        }

        // 3. Apply ColorFilter via Canvas
        val finalBitmap = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(finalBitmap)
        val composeMatrix = buildComposeColorMatrix(filter, brightness, contrast, saturation)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = AndroidColorMatrixFilter(AndroidColorMatrix(composeMatrix.values))
        }
        canvas.drawBitmap(working, 0f, 0f, paint)

        val outFile = File(
            sourceFile.parentFile,
            "${sourceFile.nameWithoutExtension}_pro_${System.currentTimeMillis() % 10000}.jpg"
        )
        FileOutputStream(outFile).use { fos ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), fos)
        }
        outFile
    } catch (_: Exception) {
        null
    }
}
