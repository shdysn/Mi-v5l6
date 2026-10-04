package com.mi.explorer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.data.repository.StatusMediaItem
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.utils.FileIconHelper
import java.io.File

enum class StatusTab {
    PHOTOS,
    VIDEOS,
    SAVED,
    SENT_CLEANER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSaverScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statuses by viewModel.activeStatuses.collectAsStateWithLifecycle()
    val savedStatuses by viewModel.savedStatuses.collectAsStateWithLifecycle()
    val sentMedia by viewModel.sentMediaSummary.collectAsStateWithLifecycle()
    val isLoading by viewModel.isStatusLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(StatusTab.PHOTOS) }
    var previewTarget by remember { mutableStateOf<StatusMediaItem?>(null) }
    var showSentCleanConfirm by remember { mutableStateOf(false) }

    val photos = remember(statuses) { statuses.filter { !it.isVideo } }
    val videos = remember(statuses) { statuses.filter { it.isVideo } }

    Scaffold(
        modifier = modifier.testTag("status_saver_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Status Saver & Social") },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("status_saver_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStatuses() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MiOrange)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Xiaomi MIUI styled Tab Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == StatusTab.PHOTOS,
                    onClick = { selectedTab = StatusTab.PHOTOS },
                    text = { Text("Photos (${photos.size})") }
                )
                Tab(
                    selected = selectedTab == StatusTab.VIDEOS,
                    onClick = { selectedTab = StatusTab.VIDEOS },
                    text = { Text("Videos (${videos.size})") }
                )
                Tab(
                    selected = selectedTab == StatusTab.SAVED,
                    onClick = { selectedTab = StatusTab.SAVED },
                    text = { Text("Saved (${savedStatuses.size})") }
                )
                Tab(
                    selected = selectedTab == StatusTab.SENT_CLEANER,
                    onClick = { selectedTab = StatusTab.SENT_CLEANER },
                    text = { Text("Sent Media (${sentMedia.formattedTotalSize})") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MiOrange)
                }
            } else when (selectedTab) {
                StatusTab.PHOTOS -> {
                    StatusGrid(
                        items = photos,
                        emptyText = "No active WhatsApp photos found.\nOpen WhatsApp and view statuses first!",
                        onPreview = { previewTarget = it },
                        onSave = { viewModel.saveStatusItem(it.file) },
                        onShare = { shareMediaFile(context, it.file, false) }
                    )
                }
                StatusTab.VIDEOS -> {
                    StatusGrid(
                        items = videos,
                        emptyText = "No active WhatsApp videos found.\nOpen WhatsApp and view video statuses first!",
                        onPreview = { previewTarget = it },
                        onSave = { viewModel.saveStatusItem(it.file) },
                        onShare = { shareMediaFile(context, it.file, true) }
                    )
                }
                StatusTab.SAVED -> {
                    StatusGrid(
                        items = savedStatuses,
                        emptyText = "No saved statuses yet.\nTap the download icon on any status to save it to your Gallery!",
                        onPreview = { previewTarget = it },
                        onSave = null,
                        onShare = { shareMediaFile(context, it.file, it.isVideo) }
                    )
                }
                StatusTab.SENT_CLEANER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MiOrange.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MiOrange, modifier = Modifier.size(32.dp))
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Redundant Sent Media",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${sentMedia.totalFiles} sent photos & videos consuming ${sentMedia.formattedTotalSize}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { showSentCleanConfirm = true },
                                    enabled = sentMedia.totalFiles > 0,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Clean Sent Media (${sentMedia.formattedTotalSize})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSentCleanConfirm) {
        AlertDialog(
            onDismissRequest = { showSentCleanConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444)) },
            title = { Text("Clean WhatsApp Sent Media?") },
            text = { Text("This will delete ${sentMedia.totalFiles} duplicate sent photos and videos to free up ${sentMedia.formattedTotalSize}. Your original received photos will remain safe.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSentCleanConfirm = false
                        viewModel.cleanSentMediaFiles(sentMedia.files)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clean Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSentCleanConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    previewTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { previewTarget = null },
            title = { Text(if (target.isVideo) "Video Status" else "Photo Status") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FileIconHelper.FileIconBadge(
                        file = target.file,
                        size = 180.dp,
                        iconSize = 48.dp,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${target.sourceApp} • ${target.formattedSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveStatusItem(target.file)
                        previewTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save to Gallery")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewTarget = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun StatusGrid(
    items: List<StatusMediaItem>,
    emptyText: String,
    onPreview: (StatusMediaItem) -> Unit,
    onSave: ((StatusMediaItem) -> Unit)?,
    onShare: (StatusMediaItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.HideImage, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.file.absolutePath }) { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPreview(item) },
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FileIconHelper.FileIconBadge(
                            file = item.file,
                            size = 110.dp,
                            iconSize = 32.dp,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating action buttons on status thumbnail
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (onSave != null) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MiOrange)
                                        .clickable { onSave(item) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { onShare(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareMediaFile(context: android.content.Context, file: File, isVideo: Boolean) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
    } catch (e: Exception) {
        // Fallback
    }
}
