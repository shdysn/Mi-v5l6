package com.mi.explorer.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.*
import com.mi.explorer.ui.components.*
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.ui.viewmodel.Screen
import com.mi.explorer.ui.viewmodel.StorageTabState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val storageSpace by viewModel.storageSpace.collectAsStateWithLifecycle()
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val isRecentLoading by viewModel.isRecentLoading.collectAsStateWithLifecycle()
    val clipboardState by viewModel.clipboard.collectAsStateWithLifecycle()
    val isAmoled by viewModel.isAmoledMode.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    val isDualPaneActive by viewModel.isDualPaneActive.collectAsStateWithLifecycle()
    val paneBState by viewModel.paneBState.collectAsStateWithLifecycle()
    val activePaneIndex by viewModel.activePaneIndex.collectAsStateWithLifecycle()
    val storageVolumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
    val selectedVolume by viewModel.selectedVolume.collectAsStateWithLifecycle()
    val fileTagsMap by viewModel.fileTagsMap.collectAsStateWithLifecycle()
    val selectedTagFilter by viewModel.selectedTagFilter.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var recentFilter by remember { mutableStateOf("All") }

    // Dialog states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFileName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    var deleteTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }
    var checksumTarget by remember { mutableStateOf<FileItem?>(null) }
    var zipTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var zipArchiveName by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var openWithTarget by remember { mutableStateOf<FileItem?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var tagTarget by remember { mutableStateOf<FileItem?>(null) }
    var exifCleanerTarget by remember { mutableStateOf<FileItem?>(null) }

    Scaffold(
        modifier = modifier.testTag("main_screen"),
        topBar = {
            if (isSearchActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = storageState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search files & folders...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MiOrange) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.setSearchQuery("")
                                    isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mi_search_field")
                        )
                    }
                }
            } else {
                MiTopHeader(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    onSearchClick = { isSearchActive = true },
                    onCleanerClick = { viewModel.openCleaner() },
                    onFtpClick = { viewModel.openFtpServer() },
                    onVaultClick = { viewModel.openVault() },
                    onDuplicatesClick = { viewModel.openDuplicateFinder() },
                    onAnalyzerClick = { viewModel.openStorageAnalyzer() },
                    onTrashClick = { viewModel.openTrash() },
                    onNetworkDrivesClick = { viewModel.openNetworkDrives() },
                    onFastShareClick = { viewModel.openFastShare() },
                    onDualPaneToggle = { viewModel.toggleDualPane() },
                    isDualPaneActive = isDualPaneActive,
                    onAmoledToggle = { viewModel.toggleAmoledMode() },
                    isAmoled = isAmoled
                )
            }
        },
        bottomBar = {
            MiClipboardBar(
                clipboardState = clipboardState,
                onPaste = { viewModel.pasteToCurrentDirectory() },
                onClear = { viewModel.clearClipboard() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val context = LocalContext.current
            var hasAllFilesAccess by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else true
                )
            }

            val requestAllFilesLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    hasAllFilesAccess = Environment.isExternalStorageManager()
                    if (hasAllFilesAccess) {
                        viewModel.onStoragePermissionGranted()
                    }
                }
            }

            AnimatedVisibility(visible = !hasAllFilesAccess) {
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
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All Files Access",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Allow storage access to view and manage your files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        requestAllFilesLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        requestAllFilesLauncher.launch(fallback)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            when (selectedTab) {
                MiTab.RECENT -> {
                    // Recent Tab Content
                    RecentTabContent(
                        recentFiles = recentFiles,
                        isLoading = isRecentLoading,
                        activeFilter = recentFilter,
                        onFilterSelected = { recentFilter = it },
                        onOpenFile = { item ->
                            if (!viewModel.openFileSmart(item, recentFiles)) {
                                openWithTarget = item
                            }
                        },
                        onMenuAction = { action, item ->
                            when (action) {
                                "open" -> {
                                    if (!viewModel.openFileSmart(item, recentFiles)) {
                                        openWithTarget = item
                                    }
                                }
                                "open_with" -> openWithTarget = item
                                "toggle_favorite" -> viewModel.toggleFavorite(item.file)
                                "pin_home" -> {
                                    val pinned = com.mi.explorer.utils.ShortcutHelper.pinFileOrFolderToHomeScreen(context, item)
                                    viewModel.showMessage(if (pinned) "Shortcut request sent to Home Screen" else "Pinned shortcut not supported on this launcher")
                                }
                                "checksum" -> checksumTarget = item
                                "vault" -> viewModel.addFileToVault(item)
                                "tags" -> tagTarget = item
                                "clean_exif" -> exifCleanerTarget = item
                                "fast_share" -> viewModel.openFastShare(listOf(item))
                                "shred" -> viewModel.openFileShredder(listOf(item.file))
                                "copy" -> viewModel.copySingle(item)
                                "cut" -> viewModel.cutSingle(item)
                                "rename" -> {
                                    renameTarget = item
                                    renameNewName = item.name
                                }
                                "delete" -> deleteTargets = listOf(item)
                                "details" -> detailsTarget = item
                                "zip" -> {
                                    zipArchiveName = "${item.name}.zip"
                                    zipTargets = listOf(item)
                                }
                                "unzip" -> viewModel.openZipViewer(item.file)
                            }
                        },
                        onRefresh = { viewModel.loadRecentFiles() }
                    )
                }
                MiTab.STORAGE -> {
                    // Storage Tab Content (MIUI Categories & Folder Navigation)
                    StorageTabContent(
                        storageSpace = storageSpace,
                        storageState = storageState,
                        rootStorageDir = viewModel.fileRepository.rootStorageDirectory,
                        favorites = favorites,
                        isDualPaneActive = isDualPaneActive,
                        paneBState = paneBState,
                        activePaneIndex = activePaneIndex,
                        onSelectPane = { viewModel.setActivePane(it) },
                        onNavigatePaneA = { viewModel.loadDirectory(it, addToHistory = true) },
                        onNavigatePaneB = { viewModel.navigatePaneB(it) },
                        onBackPaneA = { viewModel.goBackInDirectory() },
                        onBackPaneB = { viewModel.backPaneB() },
                        onCopyAtoB = { viewModel.copyPaneAtoB() },
                        onCopyBtoA = { viewModel.copyPaneBtoA() },
                        onMoveAtoB = { viewModel.movePaneAtoB() },
                        onMoveBtoA = { viewModel.movePaneBtoA() },
                        onToggleSelectB = { viewModel.toggleSelectPaneB(it) },
                        storageVolumes = storageVolumes,
                        selectedVolume = selectedVolume,
                        onSwitchVolume = { viewModel.switchStorageVolume(it) },
                        fileTagsMap = fileTagsMap,
                        selectedTagFilter = selectedTagFilter,
                        onFilterTag = { viewModel.filterByTag(it) },
                        onFastShareSelected = { viewModel.openFastShare(storageState.selectedItems.toList()) },
                        onCleanClick = { viewModel.openCleaner() },
                        onTrashClick = { viewModel.openTrash() },
                        onNetworkDrivesClick = { viewModel.openNetworkDrives() },
                        onFastShareClick = { viewModel.openFastShare() },
                        onFtpClick = { viewModel.openFtpServer() },
                        onDualPaneToggle = { viewModel.toggleDualPane() },
                        onCategoryClick = { cat, title ->
                            if (cat == FileCategory.APK) {
                                viewModel.openAppInstaller()
                            } else {
                                viewModel.openCategory(cat, title)
                            }
                        },
                        onSocialClick = { viewModel.openSocialHub() },
                        onAppManagerClick = { viewModel.openAppManager() },
                        onAppInstallerClick = { viewModel.openAppInstaller() },
                        onRootBrowserClick = { viewModel.openRootBrowser() },
                        onVaultClick = { viewModel.openVault() },
                        onDuplicatesClick = { viewModel.openDuplicateFinder() },
                        onAnalyzerClick = { viewModel.openStorageAnalyzer() },
                        onWebShareClick = { viewModel.openWebShare() },
                        onStatusSaverClick = { viewModel.openStatusSaver() },
                        onFileShredderClick = { viewModel.openFileShredder() },
                        onSmartCollectionsClick = { viewModel.openSmartCollections() },
                        onTimeMachineClick = { viewModel.openTimeMachine() },
                        onPinWidgetClick = {
                            val ok = com.mi.explorer.utils.ShortcutHelper.requestPinStorageWidget(context)
                            viewModel.showMessage(if (ok) "Home Screen Storage Widget prompt opened!" else "Long-press Home Screen -> Widgets -> Mi Explorer")
                        },
                        onBatchRename = { showBatchRenameDialog = true },
                        onNavigateTo = { viewModel.loadDirectory(it, addToHistory = true) },
                        onNavigateUp = {
                            val parent = storageState.currentDir.parentFile
                            if (parent != null && parent.canRead()) {
                                viewModel.loadDirectory(parent, addToHistory = true)
                            }
                        },
                        onOpenFile = { item ->
                            if (!viewModel.openFileSmart(item, storageState.items)) {
                                openWithTarget = item
                            }
                        },
                        onToggleSelect = { viewModel.toggleSelectItem(it) },
                        onSelectAll = { viewModel.selectAll() },
                        onClearSelection = { viewModel.clearSelection() },
                        onCopySelected = { viewModel.copySelected() },
                        onCutSelected = { viewModel.cutSelected() },
                        onDeleteSelected = { deleteTargets = storageState.selectedItems.toList() },
                        onZipSelected = {
                            zipArchiveName = "${storageState.currentDir.name}.zip"
                            zipTargets = storageState.selectedItems.toList()
                        },
                        onNewFolder = {
                            newFolderName = ""
                            showCreateFolderDialog = true
                        },
                        onNewFile = {
                            newFileName = ""
                            showCreateFileDialog = true
                        },
                        onToggleViewMode = { viewModel.toggleViewMode() },
                        onShowSortMenu = { showSortMenu = true },
                        onToggleBigFilesFilter = { viewModel.toggleBigFilesFilter() },
                        onMenuAction = { action, item ->
                            when (action) {
                                "open" -> {
                                    if (!viewModel.openFileSmart(item, storageState.items)) {
                                        openWithTarget = item
                                    }
                                }
                                "open_with" -> openWithTarget = item
                                "toggle_favorite" -> viewModel.toggleFavorite(item.file)
                                "pin_home" -> {
                                    val pinned = com.mi.explorer.utils.ShortcutHelper.pinFileOrFolderToHomeScreen(context, item)
                                    viewModel.showMessage(if (pinned) "Shortcut request sent to Home Screen" else "Pinned shortcut not supported on this launcher")
                                }
                                "checksum" -> checksumTarget = item
                                "vault" -> viewModel.addFileToVault(item)
                                "tags" -> tagTarget = item
                                "clean_exif" -> exifCleanerTarget = item
                                "fast_share" -> viewModel.openFastShare(listOf(item))
                                "shred" -> viewModel.openFileShredder(listOf(item.file))
                                "copy" -> viewModel.copySingle(item)
                                "cut" -> viewModel.cutSingle(item)
                                "rename" -> {
                                    renameTarget = item
                                    renameNewName = item.name
                                }
                                "delete" -> deleteTargets = listOf(item)
                                "details" -> detailsTarget = item
                                "zip" -> {
                                    zipArchiveName = "${item.name}.zip"
                                    zipTargets = listOf(item)
                                }
                                "unzip" -> viewModel.openZipViewer(item.file)
                            }
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName)
                            showCreateFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("New File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File Name (e.g. note.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createTextFile(newFileName)
                            showCreateFileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameNewName.isNotBlank() && renameNewName != item.name) {
                            viewModel.renameItem(item, renameNewName)
                            renameTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTargets?.let { targets ->
        var moveToBin by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = { Text(if (moveToBin) "Move to Recycle Bin" else "Delete Permanently") },
            text = {
                Column {
                    Text(
                        if (moveToBin)
                            "Move ${targets.size} item(s) to Recycle Bin? You can restore them anytime."
                        else
                            "Permanently delete ${targets.size} item(s)? This action cannot be undone."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
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
                            viewModel.moveToTrash(targets)
                        } else {
                            viewModel.deleteItems(targets)
                        }
                        deleteTargets = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (moveToBin) MiOrange else Color(0xFFEF4444)
                    )
                ) {
                    Text(if (moveToBin) "Move to Bin" else "Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    detailsTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text("Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Name: ${item.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Location: ${item.path}", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Size: ${item.formattedSize} (${java.lang.String.format(java.util.Locale.US, "%,d", item.effectiveSize)} bytes)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (item.isLarge) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (item.isLarge) MiOrange else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (item.isDirectory) {
                        Text(text = "Items count: ${item.itemCount}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(text = "Type: ${item.friendlyTypeLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Modified: ${item.formattedDate}", style = MaterialTheme.typography.bodySmall)
                    if (!item.isDirectory) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                checksumTarget = item
                                detailsTarget = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp), tint = MiOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calculate Checksum (MD5/SHA)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsTarget = null }) {
                    Text("OK")
                }
            }
        )
    }

    checksumTarget?.let { item ->
        ChecksumDialog(
            item = item,
            onDismiss = { checksumTarget = null }
        )
    }

    tagTarget?.let { item ->
        val currentTagIds = fileTagsMap[item.path] ?: emptyList()
        val currentTags = currentTagIds.mapNotNull { ColorTag.findTag(it) }
        TagSelectionDialog(
            fileItem = item,
            currentTags = currentTags,
            onToggleTag = { tag ->
                viewModel.toggleTagForFile(item.file, tag.id)
            },
            onDismiss = { tagTarget = null }
        )
    }

    exifCleanerTarget?.let { item ->
        ExifCleanerDialog(
            item = item,
            onDismiss = { exifCleanerTarget = null },
            onCleanSaved = {
                viewModel.refreshCurrentDirectory()
                viewModel.showMessage("Photo EXIF metadata stripped!")
            }
        )
    }

    zipTargets?.let { targets ->
        ZipCompressDialog(
            selectedItems = targets,
            defaultArchiveName = zipArchiveName,
            onDismiss = { zipTargets = null },
            onCompress = { name, level ->
                val destFile = File(storageState.currentDir, name)
                viewModel.compressFilesToZip(targets.map { it.file }, destFile, level)
                zipTargets = null
            },
            onCompressPro = { name, format, level, password ->
                val destFile = File(storageState.currentDir, name)
                viewModel.compressFilesToZip(targets.map { it.file }, destFile, level, format, password)
                zipTargets = null
            }
        )
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
                { viewModel.openImageViewer(target.file, storageState.items) }
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

    if (showSortMenu) {
        MiSortBottomSheet(
            currentSortType = storageState.sortType,
            foldersOnTop = storageState.foldersOnTop,
            showHidden = storageState.showHidden,
            filterOnlyBigFiles = storageState.filterOnlyBigFiles,
            onSortTypeChange = { viewModel.setSortType(it) },
            onToggleFoldersOnTop = { viewModel.toggleFoldersOnTop() },
            onToggleShowHidden = { viewModel.toggleShowHidden() },
            onToggleBigFilesFilter = { viewModel.toggleBigFilesFilter() },
            onDismiss = { showSortMenu = false }
        )
    }

    if (showBatchRenameDialog && storageState.selectedItems.size >= 2) {
        BatchRenameDialog(
            selectedFiles = storageState.selectedItems.toList(),
            onDismiss = { showBatchRenameDialog = false },
            onApplyRename = { pairs ->
                viewModel.batchRename(pairs)
                showBatchRenameDialog = false
            }
        )
    }
}

@Composable
fun UtilityCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SortOptionRow(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        color = Color.Transparent
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun RecentTabContent(
    recentFiles: List<FileItem>,
    isLoading: Boolean,
    activeFilter: String,
    onFilterSelected: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onMenuAction: (String, FileItem) -> Unit,
    onRefresh: () -> Unit
) {
    val filters = listOf("All", "🔥 Big Files (>10MB)", "Images", "Docs", "APKs", "Archives", "Music")

    val filteredList = remember(recentFiles, activeFilter) {
        when (activeFilter) {
            "🔥 Big Files (>10MB)" -> recentFiles.filter { it.effectiveSize >= 10L * 1024 * 1024 }
            "Images" -> recentFiles.filter { it.category == FileCategory.IMAGE }
            "Docs" -> recentFiles.filter { it.category == FileCategory.DOCUMENT }
            "APKs" -> recentFiles.filter { it.category == FileCategory.APK }
            "Archives" -> recentFiles.filter { it.category == FileCategory.ARCHIVE }
            "Music" -> recentFiles.filter { it.category == FileCategory.AUDIO }
            else -> recentFiles
        }
    }

    val grouped = remember(filteredList) {
        filteredList.groupBy { it.timeGroup }
    }

    var recentViewMode by remember { mutableStateOf(ViewMode.LIST) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recent_tab_list"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Filter chips bar & View Mode toggle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { f ->
                        val isSelected = f == activeFilter
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onFilterSelected(f) }
                                .testTag("filter_chip_$f"),
                            color = if (isSelected) MiOrange else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = f,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        recentViewMode = if (recentViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (recentViewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                        contentDescription = "Toggle View Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (isLoading && filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MiOrange)
                }
            }
        } else if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No recent files found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            grouped.forEach { (timeHeader, files) ->
                item {
                    Text(
                        text = timeHeader,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                    )
                }

                if (recentViewMode == ViewMode.GRID) {
                    val gridColumns = 3
                    val chunked = files.chunked(gridColumns)
                    items(chunked, key = { row -> "recent_grid_${row.first().path}" }) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { file ->
                                Box(modifier = Modifier.weight(1f)) {
                                    MiFileGridItem(
                                        item = file,
                                        isSelected = false,
                                        isSelectionMode = false,
                                        onClick = { onOpenFile(file) },
                                        onLongClick = {},
                                        onToggleSelect = {},
                                        onMenuAction = { onMenuAction(it, file) }
                                    )
                                }
                            }
                            repeat(gridColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(files, key = { it.path }) { file ->
                        MiFileRow(
                            item = file,
                            isSelected = false,
                            isSelectionMode = false,
                            onClick = { onOpenFile(file) },
                            onLongClick = {},
                            onToggleSelect = {},
                            onMenuAction = { onMenuAction(it, file) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorageTabContent(
    storageSpace: StorageSpace,
    storageState: StorageTabState,
    rootStorageDir: File,
    favorites: List<FavoriteItem> = emptyList(),
    isDualPaneActive: Boolean = false,
    paneBState: StorageTabState? = null,
    activePaneIndex: Int = 0,
    onSelectPane: (Int) -> Unit = {},
    onNavigatePaneA: (File) -> Unit = {},
    onNavigatePaneB: (File) -> Unit = {},
    onBackPaneA: () -> Unit = {},
    onBackPaneB: () -> Unit = {},
    onCopyAtoB: () -> Unit = {},
    onCopyBtoA: () -> Unit = {},
    onMoveAtoB: () -> Unit = {},
    onMoveBtoA: () -> Unit = {},
    onToggleSelectB: (FileItem) -> Unit = {},
    storageVolumes: List<StorageVolumeItem> = emptyList(),
    selectedVolume: StorageVolumeItem? = null,
    onSwitchVolume: (StorageVolumeItem) -> Unit = {},
    fileTagsMap: Map<String, List<String>> = emptyMap(),
    selectedTagFilter: String? = null,
    onFilterTag: (String?) -> Unit = {},
    onFastShareSelected: () -> Unit = {},
    onCleanClick: () -> Unit,
    onTrashClick: () -> Unit,
    onNetworkDrivesClick: () -> Unit = {},
    onFastShareClick: () -> Unit = {},
    onFtpClick: () -> Unit = {},
    onDualPaneToggle: () -> Unit = {},
    onCategoryClick: (FileCategory, String) -> Unit,
    onSocialClick: () -> Unit = {},
    onAppManagerClick: () -> Unit,
    onAppInstallerClick: () -> Unit = {},
    onRootBrowserClick: () -> Unit = {},
    onVaultClick: () -> Unit,
    onDuplicatesClick: () -> Unit,
    onAnalyzerClick: () -> Unit,
    onWebShareClick: () -> Unit = {},
    onStatusSaverClick: () -> Unit = {},
    onFileShredderClick: () -> Unit = {},
    onSmartCollectionsClick: () -> Unit = {},
    onTimeMachineClick: () -> Unit = {},
    onPinWidgetClick: () -> Unit = {},
    onBatchRename: () -> Unit,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onZipSelected: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onToggleViewMode: () -> Unit,
    onShowSortMenu: () -> Unit,
    onToggleBigFilesFilter: () -> Unit = {},
    onMenuAction: (String, FileItem) -> Unit
) {
    if (isDualPaneActive && paneBState != null) {
        DualPaneView(
            paneAState = storageState,
            paneBState = paneBState,
            activePane = activePaneIndex,
            onSelectPane = onSelectPane,
            onNavigateA = onNavigatePaneA,
            onNavigateB = onNavigatePaneB,
            onBackA = onBackPaneA,
            onBackB = onBackPaneB,
            onCopyAtoB = onCopyAtoB,
            onCopyBtoA = onCopyBtoA,
            onMoveAtoB = onMoveAtoB,
            onMoveBtoA = onMoveBtoA,
            onToggleSelectA = onToggleSelect,
            onToggleSelectB = onToggleSelectB,
            onClearSelectA = onClearSelection,
            onOpenFile = onOpenFile
        )
        return
    }

    val isRoot = storageState.currentDir == rootStorageDir
    var showToolsSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("storage_tab_list"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Root Home Dashboard View
        if (isRoot) {
            // 1. Sleek Storage Card with integrated volume selector (Zero duplicate chips)
            item {
                StorageCard(
                    storageSpace = storageSpace,
                    onCleanClick = onCleanClick,
                    storageVolumes = storageVolumes,
                    selectedVolume = selectedVolume,
                    onSwitchVolume = onSwitchVolume,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 2. 8-tile MIUI Category Grid (with "Tools" tile triggering the full sheet)
            item {
                CategoryGrid(
                    onCategoryClick = onCategoryClick,
                    onToolsClick = { showToolsSheet = true },
                    onSocialClick = onSocialClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 3. Signature MIUI Utilities Carousel (Horizontal, spacious, NO text truncation)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Utilities",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showToolsSheet = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("View All", color = MiOrange, style = MaterialTheme.typography.labelMedium)
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MiOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            UtilityCard(
                                title = "Home Widget",
                                subtitle = "Pin Storage Card",
                                icon = Icons.Default.Widgets,
                                color = Color(0xFF0EA5E9),
                                onClick = onPinWidgetClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "App Installer",
                                subtitle = "Install APK / XAPK",
                                icon = Icons.Default.InstallMobile,
                                color = Color(0xFF059669),
                                onClick = onAppInstallerClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Status Saver",
                                subtitle = "WhatsApp Status",
                                icon = Icons.Default.BookmarkAdded,
                                color = Color(0xFF10B981),
                                onClick = onStatusSaverClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "PC Web Portal",
                                subtitle = "Send & Receive",
                                icon = Icons.Default.Language,
                                color = Color(0xFF2563EB),
                                onClick = onWebShareClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Smart Hubs",
                                subtitle = "Auto Collections",
                                icon = Icons.Default.AutoAwesomeMosaic,
                                color = Color(0xFF8B5CF6),
                                onClick = onSmartCollectionsClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Time Machine",
                                subtitle = "On This Day",
                                icon = Icons.Default.History,
                                color = Color(0xFFF59E0B),
                                onClick = onTimeMachineClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "File Shredder",
                                subtitle = "DoD 3-Pass Wipe",
                                icon = Icons.Default.EnhancedEncryption,
                                color = Color(0xFFEF4444),
                                onClick = onFileShredderClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Private Vault",
                                subtitle = "Fingerprint safe",
                                icon = Icons.Default.Lock,
                                color = MiOrange,
                                onClick = onVaultClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Mi Fast Share",
                                subtitle = "Direct Wi-Fi",
                                icon = Icons.Default.WifiTethering,
                                color = Color(0xFF10B981),
                                onClick = onFastShareClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "APK Cloner & Hub",
                                subtitle = "Backup & Rollback",
                                icon = Icons.Default.Android,
                                color = Color(0xFF8B5CF6),
                                onClick = onAppManagerClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Cloud Drives",
                                subtitle = "SMB / WebDAV",
                                icon = Icons.Default.CloudQueue,
                                color = Color(0xFF0EA5E9),
                                onClick = onNetworkDrivesClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Recycle Bin",
                                subtitle = "30d auto-purge",
                                icon = Icons.Default.DeleteOutline,
                                color = Color(0xFFEF4444),
                                onClick = onTrashClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Analyzer",
                                subtitle = "Storage map",
                                icon = Icons.Default.PieChart,
                                color = Color(0xFF3B82F6),
                                onClick = onAnalyzerClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Duplicates",
                                subtitle = "Clean redundant",
                                icon = Icons.Default.ContentCopy,
                                color = Color(0xFF14B8A6),
                                onClick = onDuplicatesClick
                            )
                        }
                        item {
                            UtilityCard(
                                title = "Transfer to PC",
                                subtitle = "FTP server",
                                icon = Icons.Default.Wifi,
                                color = Color(0xFF6366F1),
                                onClick = onFtpClick
                            )
                        }
                    }
                }
            }

            // Favorites & Pinned Folders Bar
            if (favorites.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Favorites & Quick Access",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(favorites, key = { it.path }) { fav ->
                                val (favIcon, favColor) = getFileItemIconAndColor(FileItem(fav.file))
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onNavigateTo(fav.file) },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    tonalElevation = 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = favIcon,
                                            contentDescription = null,
                                            tint = favColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = fav.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        // Section header for Files & Folders
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isRoot) "Files & Folders" else storageState.currentDir.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${storageState.displayItems.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = storageState.filterOnlyBigFiles,
                        onClick = onToggleBigFilesFilter,
                        label = {
                            Text(
                                text = "🔥 Big (>10MB)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (storageState.filterOnlyBigFiles) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MiOrange,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (storageState.bigItemsCount > 0) MiOrange.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MiOrange,
                            enabled = true,
                            selected = storageState.filterOnlyBigFiles
                        )
                    )

                    if (selectedTagFilter != null) {
                        Surface(
                            color = MiOrange.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { onFilterTag(null) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tag: $selectedTagFilter ✕",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MiOrange
                                )
                            }
                        }
                    }
                }
            }
        }

        // Color Tags Quick Filter (When browsing folders or when a tag is active)
        if (!isRoot || selectedTagFilter != null) {
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTagFilter == null,
                            onClick = { onFilterTag(null) },
                            label = { Text("All Files") }
                        )
                    }
                    items(ColorTag.PRESET_TAGS) { tag ->
                        val isSelected = selectedTagFilter == tag.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterTag(if (isSelected) null else tag.id) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(tag.composeColor)
                                )
                            },
                            label = { Text(tag.name) }
                        )
                    }
                }
            }
        }

        // Folder Path Breadcrumbs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRoot) {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("navigate_up_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Up",
                            tint = MiOrange
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                MiBreadcrumbs(
                    currentDir = storageState.currentDir,
                    rootStorageDir = rootStorageDir,
                    onNavigateTo = onNavigateTo,
                    modifier = Modifier.weight(1f)
                )

                // Interactive Sort Pill
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onShowSortMenu)
                        .testTag("sort_pill_button"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = MiOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = storageState.sortType.chipLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onToggleViewMode, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (storageState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                        contentDescription = "View Mode"
                    )
                }
            }
        }

        // Action Toolbar (New Folder, New File, Select All, Batch Rename)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (storageState.isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${storageState.selectedItems.size} selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MiOrange)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onFastShareSelected, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.WifiTethering, contentDescription = "Fast Share", tint = Color(0xFF10B981))
                        }
                        if (storageState.selectedItems.size >= 2) {
                            IconButton(onClick = onBatchRename, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Batch Rename", tint = MiOrange)
                            }
                        }
                        IconButton(onClick = onCopySelected, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = onCutSelected, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Cut")
                        }
                        IconButton(onClick = onZipSelected, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Archive, contentDescription = "Zip")
                        }
                        IconButton(onClick = onDeleteSelected, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                        IconButton(onClick = onClearSelection, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = onNewFolder,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp).testTag("action_new_folder")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Folder", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledTonalButton(
                            onClick = onNewFile,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp).testTag("action_new_file")
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New File", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    TextButton(onClick = onSelectAll) {
                        Text("Select All", style = MaterialTheme.typography.labelMedium, color = MiOrange)
                    }
                }
            }
        }

        // Folder files listing
        if (storageState.isLoading && storageState.items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MiOrange)
                }
            }
        } else if (storageState.displayItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (storageState.filterOnlyBigFiles) "No big items (>10MB) in this folder" else "This folder is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (storageState.filterOnlyBigFiles) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onToggleBigFilesFilter) {
                                Text("Show all ${storageState.items.size} items", color = MiOrange)
                            }
                        }
                    }
                }
            }
        } else if (storageState.viewMode == ViewMode.GRID) {
            val gridColumns = 3
            val chunked = storageState.displayItems.chunked(gridColumns)
            items(chunked, key = { row -> "storage_grid_${row.first().path}" }) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        val isSelected = storageState.selectedItems.contains(item)
                        val itemTagIds = fileTagsMap[item.path] ?: emptyList()
                        val itemTags = itemTagIds.mapNotNull { ColorTag.findTag(it) }
                        Box(modifier = Modifier.weight(1f)) {
                            MiFileGridItem(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = storageState.isSelectionMode,
                                tags = itemTags,
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigateTo(item.file)
                                    } else {
                                        onOpenFile(item)
                                    }
                                },
                                onLongClick = { onToggleSelect(item) },
                                onToggleSelect = { onToggleSelect(item) },
                                onMenuAction = { onMenuAction(it, item) }
                            )
                        }
                    }
                    repeat(gridColumns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(storageState.displayItems, key = { it.path }) { item ->
                val isSelected = storageState.selectedItems.contains(item)
                val itemTagIds = fileTagsMap[item.path] ?: emptyList()
                val itemTags = itemTagIds.mapNotNull { ColorTag.findTag(it) }
                MiFileRow(
                    item = item,
                    isSelected = isSelected,
                    isSelectionMode = storageState.isSelectionMode,
                    tags = itemTags,
                    onClick = {
                        if (item.isDirectory) {
                            onNavigateTo(item.file)
                        } else {
                            onOpenFile(item)
                        }
                    },
                    onLongClick = { onToggleSelect(item) },
                    onToggleSelect = { onToggleSelect(item) },
                    onMenuAction = { onMenuAction(it, item) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    if (showToolsSheet) {
        MiToolsBottomSheet(
            onDismiss = { showToolsSheet = false },
            onVaultClick = onVaultClick,
            onFastShareClick = onFastShareClick,
            onNetworkDrivesClick = onNetworkDrivesClick,
            onTrashClick = onTrashClick,
            onAnalyzerClick = onAnalyzerClick,
            onDuplicatesClick = onDuplicatesClick,
            onCleanerClick = onCleanClick,
            onAppManagerClick = onAppManagerClick,
            onAppInstallerClick = onAppInstallerClick,
            onRootBrowserClick = onRootBrowserClick,
            onFtpClick = onFtpClick,
            onDualPaneToggle = onDualPaneToggle,
            isDualPaneActive = isDualPaneActive,
            onSocialClick = onSocialClick,
            onPinWidgetClick = onPinWidgetClick,
            onWebShareClick = onWebShareClick,
            onStatusSaverClick = onStatusSaverClick,
            onFileShredderClick = onFileShredderClick,
            onSmartCollectionsClick = onSmartCollectionsClick,
            onTimeMachineClick = onTimeMachineClick
        )
    }
}

fun handleOpenFile(
    item: FileItem,
    siblingItems: List<FileItem>,
    viewModel: ExplorerViewModel
) {
    when (item.category) {
        FileCategory.CODE, FileCategory.DOCUMENT -> {
            if (item.extension in listOf("txt", "md", "json", "xml", "kt", "java", "py", "sh", "html", "css", "js", "log", "csv")) {
                viewModel.openTextEditor(item.file)
            } else {
                viewModel.showMessage("Opening ${item.name}...")
            }
        }
        FileCategory.IMAGE -> {
            viewModel.openImageViewer(item.file, siblingItems)
        }
        FileCategory.ARCHIVE -> {
            viewModel.openZipViewer(item.file)
        }
        FileCategory.APK -> {
            viewModel.openApkInstallDialog(item.file)
        }
        else -> {
            viewModel.openTextEditor(item.file)
        }
    }
}
