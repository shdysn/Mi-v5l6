package com.mi.explorer.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.components.ExifCleanerDialog
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.imageViewerState.collectAsStateWithLifecycle()
    var showInfoDialog by remember { mutableStateOf(false) }
    var showExifCleaner by remember { mutableStateOf(false) }

    val bitmap = remember(state.currentFile?.absolutePath) {
        state.currentFile?.let { file ->
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("image_viewer_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.currentFile?.name ?: "Photos",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (state.imageList.isNotEmpty()) {
                            Text(
                                text = "${state.currentIndex + 1} of ${state.imageList.size}",
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
                    IconButton(onClick = { showExifCleaner = true }) {
                        Icon(Icons.Default.Security, contentDescription = "EXIF Privacy Cleaner", tint = Color(0xFF10B981))
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Details")
                    }
                }
            )
        },
        bottomBar = {
            if (state.imageList.size > 1) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.prevImage() },
                            enabled = state.currentIndex > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = MiOrange, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("prev_image_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }

                        Button(
                            onClick = { viewModel.nextImage() },
                            enabled = state.currentIndex < state.imageList.size - 1,
                            colors = ButtonDefaults.buttonColors(containerColor = MiOrange, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("next_image_button")
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
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
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = state.currentFile?.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "Unable to load preview",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }
    }

    if (showInfoDialog && state.currentFile != null) {
        val f = state.currentFile!!
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Photo Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "File: ${f.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Path: ${f.absolutePath}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Size: ${FileItem.formatBytes(f.length())}", style = MaterialTheme.typography.bodyMedium)
                    if (bitmap != null) {
                        Text(text = "Resolution: ${bitmap.width} × ${bitmap.height}", style = MaterialTheme.typography.bodyMedium)
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
                viewModel.showMessage("Photo metadata stripped successfully!")
            }
        )
    }
}
