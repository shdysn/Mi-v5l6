package com.mi.explorer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.ApkTab
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.data.model.SortType
import com.mi.explorer.data.model.ViewMode
import com.mi.explorer.data.model.sortFileList
import com.mi.explorer.ui.components.ChecksumDialog
import com.mi.explorer.ui.components.MiFileGridItem
import com.mi.explorer.ui.components.MiFileRow
import com.mi.explorer.ui.components.MiSortBottomSheet
import com.mi.explorer.ui.components.OpenFileChooserDialog
import com.mi.explorer.ui.theme.MiGreen
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryViewScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.categoryViewState.collectAsStateWithLifecycle()
    var openWithTarget by remember { mutableStateOf<FileItem?>(null) }
    var checksumTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItem?>(null) }
    var showCategorySort by remember { mutableStateOf(false) }
    var categorySortType by remember { mutableStateOf(SortType.DATE_NEWEST) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    val sortedItems = remember(state.items, categorySortType) {
        sortFileList(state.items, categorySortType, foldersOnTop = false)
    }

    Scaffold(
        modifier = modifier.testTag("category_view_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = state.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${state.items.size} files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("category_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "Toggle View Mode",
                            tint = MiOrange
                        )
                    }
                    IconButton(onClick = { showCategorySort = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MiOrange)
                    }
                    if (state.category == FileCategory.APK) {
                        FilledTonalButton(
                            onClick = { viewModel.openAppInstaller() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("App Installer", style = MaterialTheme.typography.labelMedium)
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
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MiOrange)
                }
            } else if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No ${state.title} found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (viewMode == ViewMode.GRID) {
                val gridColumns = 3
                val chunked = sortedItems.chunked(gridColumns)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chunked, key = { row -> "cat_grid_${row.first().path}" }) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    MiFileGridItem(
                                        item = item,
                                        isSelected = false,
                                        isSelectionMode = false,
                                        onClick = {
                                            if (!viewModel.openFileSmart(item, state.items)) {
                                                openWithTarget = item
                                            }
                                        },
                                        onLongClick = {},
                                        onToggleSelect = {},
                                        onMenuAction = { action ->
                                            when (action) {
                                                "open" -> {
                                                    if (!viewModel.openFileSmart(item, state.items)) {
                                                        openWithTarget = item
                                                    }
                                                }
                                                "open_with" -> openWithTarget = item
                                                "toggle_favorite" -> viewModel.toggleFavorite(item.file)
                                                "checksum" -> checksumTarget = item
                                                "copy" -> viewModel.copySingle(item)
                                                "cut" -> viewModel.cutSingle(item)
                                                "delete" -> deleteTarget = item
                                                else -> {}
                                            }
                                        }
                                    )
                                }
                            }
                            repeat(gridColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sortedItems, key = { it.path }) { item ->
                        MiFileRow(
                            item = item,
                            isSelected = false,
                            isSelectionMode = false,
                            onClick = {
                                if (!viewModel.openFileSmart(item, state.items)) {
                                    openWithTarget = item
                                }
                            },
                            onLongClick = {},
                            onToggleSelect = {},
                            onMenuAction = { action ->
                                when (action) {
                                    "open" -> {
                                        if (!viewModel.openFileSmart(item, state.items)) {
                                            openWithTarget = item
                                        }
                                    }
                                    "open_with" -> openWithTarget = item
                                    "toggle_favorite" -> viewModel.toggleFavorite(item.file)
                                    "checksum" -> checksumTarget = item
                                    "copy" -> viewModel.copySingle(item)
                                    "cut" -> viewModel.cutSingle(item)
                                    "delete" -> deleteTarget = item
                                    else -> {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    openWithTarget?.let { target ->
        val builtInLabel = when (target.category) {
            FileCategory.IMAGE -> "View in Mi Gallery (Built-in)"
            FileCategory.CODE, FileCategory.DOCUMENT -> {
                if (target.extension in listOf("txt", "md", "json", "xml", "kt", "java", "py", "sh", "html", "css", "js", "log", "csv")) {
                    "Edit in Mi Text Editor (Built-in)"
                } else null
            }
            FileCategory.ARCHIVE -> "Inspect & Extract with Mi Zip"
            FileCategory.APK -> "Install / Inspect Package (Built-in)"
            else -> null
        }
        val builtInAction: (() -> Unit)? = when (target.category) {
            FileCategory.IMAGE -> {
                { viewModel.openImageViewer(target.file, state.items) }
            }
            FileCategory.CODE, FileCategory.DOCUMENT -> {
                if (target.extension in listOf("txt", "md", "json", "xml", "kt", "java", "py", "sh", "html", "css", "js", "log", "csv")) {
                    { viewModel.openTextEditor(target.file) }
                } else null
            }
            FileCategory.ARCHIVE -> {
                { viewModel.openZipViewer(target.file) }
            }
            FileCategory.APK -> {
                { viewModel.openApkInstallDialog(target.file) }
            }
            else -> null
        }

        OpenFileChooserDialog(
            item = target,
            onDismiss = { openWithTarget = null },
            onOpenBuiltIn = builtInAction,
            builtInActionLabel = builtInLabel
        )
    }

    checksumTarget?.let { item ->
        ChecksumDialog(
            item = item,
            onDismiss = { checksumTarget = null }
        )
    }

    deleteTarget?.let { item ->
        var moveToBin by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (moveToBin) "Move to Recycle Bin" else "Delete Permanently") },
            text = {
                Column {
                    Text(
                        if (moveToBin)
                            "Move \"${item.name}\" to Recycle Bin? You can restore it anytime."
                        else
                            "Permanently delete \"${item.name}\"? This action cannot be undone."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { moveToBin = !moveToBin }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = moveToBin,
                            onCheckedChange = { moveToBin = it },
                            colors = CheckboxDefaults.colors(checkedColor = MiOrange)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Send to Recycle Bin (Recommended)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (moveToBin) {
                            viewModel.moveToTrash(listOf(item))
                        } else {
                            viewModel.deleteItems(listOf(item))
                        }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (moveToBin) MiOrange else Color(0xFFEF4444)
                    )
                ) {
                    Text(if (moveToBin) "Move to Bin" else "Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCategorySort) {
        MiSortBottomSheet(
            currentSortType = categorySortType,
            foldersOnTop = false,
            showHidden = false,
            filterOnlyBigFiles = false,
            onSortTypeChange = { categorySortType = it },
            onToggleFoldersOnTop = {},
            onToggleShowHidden = {},
            onToggleBigFilesFilter = {},
            onDismiss = { showCategorySort = false }
        )
    }
}
