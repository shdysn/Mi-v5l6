package com.mi.explorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.data.repository.ZipEntryItem
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipViewerScreen(viewModel: ExplorerViewModel) {
    val zipState by viewModel.zipViewerState.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isZipExtracting.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    val selectedEntries = remember { mutableStateListOf<String>() }
    var showExtractDestinationDialog by remember { mutableStateOf(false) }

    val archive = zipState.archiveInfo

    val filteredEntries = remember(archive?.entries, searchQuery) {
        val list = archive?.entries ?: emptyList()
        if (searchQuery.isBlank()) list
        else list.filter { it.name.contains(searchQuery, ignoreCase = true) || it.fullPath.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = archive?.archiveName ?: "Zip Archive",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (archive != null) {
                            Text(
                                text = "${archive.totalEntries} items • ${archive.formattedArchiveSize}",
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
                    if (archive != null && !zipState.isLoading) {
                        FilledTonalButton(
                            onClick = { showExtractDestinationDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MiOrange,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedEntries.isNotEmpty()) "Extract (${selectedEntries.size})" else "Extract All")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (zipState.isLoading || isExtracting) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MiOrange)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isExtracting) "Extracting files..." else "Reading archive contents...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (archive == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Failed to load Zip Archive",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = zipState.errorMessage ?: "The archive may be corrupted or unreadable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Archive Overview Header Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MiOrange.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = MiOrange,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Archive Compression",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "${archive.formattedArchiveSize}  ➔  ${archive.formattedUncompressedSize}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (archive.overallSavingsPercentage > 0) {
                                Surface(
                                    color = Color(0xFF10B981),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${archive.overallSavingsPercentage}% Saved",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Search inside zip
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search files in archive...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MiOrange) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // Selection count & toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredEntries.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedEntries.isNotEmpty()) {
                            TextButton(onClick = { selectedEntries.clear() }) {
                                Text("Clear Selection (${selectedEntries.size})", color = MiOrange)
                            }
                        } else {
                            TextButton(onClick = {
                                selectedEntries.clear()
                                selectedEntries.addAll(filteredEntries.map { it.fullPath })
                            }) {
                                Text("Select All", color = MiOrange)
                            }
                        }
                    }

                    // Archive entries list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredEntries, key = { it.fullPath }) { entry ->
                            val isSelected = selectedEntries.contains(entry.fullPath)
                            ZipEntryRow(
                                entry = entry,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    if (isSelected) selectedEntries.remove(entry.fullPath)
                                    else selectedEntries.add(entry.fullPath)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExtractDestinationDialog && archive?.sourceFile != null) {
        val defaultTarget = remember {
            val src = archive.sourceFile
            val parent = src.parentFile
            val baseName = archive.archiveName.substringBeforeLast(".")
            if (parent != null && parent.canWrite()) {
                File(parent, baseName)
            } else {
                File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Extracted/$baseName")
            }
        }

        var destinationPath by remember { mutableStateOf(defaultTarget.absolutePath) }

        AlertDialog(
            onDismissRequest = { showExtractDestinationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, tint = MiOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extract Archive")
                }
            },
            text = {
                Column {
                    Text(
                        text = if (selectedEntries.isNotEmpty()) "Extract ${selectedEntries.size} selected items to:" else "Extract all ${archive.totalEntries} items to:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = destinationPath,
                        onValueChange = { destinationPath = it },
                        label = { Text("Destination Directory") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetDir = File(destinationPath)
                        val toExtract = if (selectedEntries.isNotEmpty()) selectedEntries.toSet() else null
                        viewModel.extractZipArchive(archive.sourceFile, targetDir, toExtract)
                        showExtractDestinationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                ) {
                    Text("Extract Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDestinationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ZipEntryRow(
    entry: ZipEntryItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleSelect),
        color = if (isSelected) MiOrange.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = MiOrange)
            )

            val descriptor = remember(entry.name, entry.isDirectory, entry.extension) {
                if (entry.isDirectory) {
                    com.mi.explorer.utils.FileIconHelper.getFolderDescriptor(entry.name)
                } else {
                    com.mi.explorer.utils.FileIconHelper.getFileDescriptor(entry.name, entry.extension)
                }
            }
            com.mi.explorer.utils.FileIconHelper.FileIconBadge(
                descriptor = descriptor,
                size = 38.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isDirectory) "Folder" else "${entry.formattedSize}  (${entry.ratioPercentage}% saved)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
