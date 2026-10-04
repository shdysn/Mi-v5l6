package com.mi.explorer.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.utils.FileOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfState by viewModel.pdfViewerState.collectAsStateWithLifecycle()
    val file = pdfState.file

    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val renderedPages = remember { mutableStateMapOf<Int, Bitmap>() }

    val listState = rememberLazyListState()

    // Zoom & pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3.5f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    // Monitor current visible item
    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPageIndex = listState.firstVisibleItemIndex
    }

    // Load PDF
    LaunchedEffect(file) {
        if (file == null || !file.exists()) {
            errorMessage = "File not found"
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        renderedPages.clear()

        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                withContext(Dispatchers.Main) {
                    pageCount = count
                    isLoading = false
                }

                // Render first batch of pages (up to 3) eagerly
                val initialBatch = minOf(count, 4)
                for (i in 0 until initialBatch) {
                    renderSinglePage(renderer, i, renderedPages)
                }

                // Render remaining pages gradually
                for (i in initialBatch until count) {
                    renderSinglePage(renderer, i, renderedPages)
                }

                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Failed to open PDF"
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("pdf_viewer_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pdfState.title.ifEmpty { file?.name ?: "PDF Viewer" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "Page ${currentPageIndex + 1} of $pageCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.handleBackPress() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (scale > 1f) {
                        IconButton(onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        }) {
                            Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom")
                        }
                    }
                    if (file != null) {
                        IconButton(onClick = {
                            val dummyItem = com.mi.explorer.data.model.FileItem(file)
                            FileOpener.shareFile(context, dummyItem)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share PDF")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE5E7EB))
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MiOrange)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading PDF document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
                        )
                    }
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.handleBackPress() }) {
                            Text("Go Back")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformState)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(pageCount) { index ->
                            val bitmap = renderedPages[index]
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 4.dp,
                                color = Color.White
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.FillWidth
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(480.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MiOrange,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Page Indicator Pill
                if (pageCount > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                            .clip(CircleShape),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentPageIndex + 1} / $pageCount",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun renderSinglePage(
    renderer: PdfRenderer,
    index: Int,
    targetMap: MutableMap<Int, Bitmap>
) = withContext(Dispatchers.IO) {
    try {
        synchronized(renderer) {
            val page = renderer.openPage(index)
            // Scale appropriately for high DPI
            val scaleFactor = 1.8f
            val destWidth = (page.width * scaleFactor).toInt().coerceAtMost(1600)
            val destHeight = (page.height * scaleFactor).toInt().coerceAtMost(2400)

            val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            targetMap[index] = bitmap
        }
    } catch (e: Exception) {
        // Ignore individual page rendering error
    }
}
