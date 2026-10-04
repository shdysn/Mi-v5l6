package com.mi.explorer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mi.explorer.data.model.ApkFileItem
import com.mi.explorer.data.model.ApkTab
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.components.ApkInstallDialog
import com.mi.explorer.ui.components.ChecksumDialog
import com.mi.explorer.ui.theme.MiBlue
import com.mi.explorer.ui.theme.MiGreen
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.utils.FileOpener
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInstallerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storageApks by viewModel.storageApks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isStorageApksLoading.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") } // ALL, READY, UPDATES, INSTALLED, DOWNGRADE
    var selectedForBatch by remember { mutableStateOf(setOf<String>()) }
    var isBatchMode by remember { mutableStateOf(false) }

    var checksumTarget by remember { mutableStateOf<FileItem?>(null) }
    var inspectingApk by remember { mutableStateOf<ApkFileItem?>(null) }
    var apkToDelete by remember { mutableStateOf<ApkFileItem?>(null) }

    // Check unknown app sources permission
    var canInstallUnknown by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else true
        )
    }

    BackHandler {
        if (isBatchMode) {
            isBatchMode = false
            selectedForBatch = emptySet()
        } else {
            viewModel.handleBackPress()
        }
    }

    // Direct File Picker for APK / XAPK / APKS
    val pickApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                try {
                    val displayName = getFileNameFromUri(context, uri)
                    val cacheFile = File(context.cacheDir, "install_$displayName").apply {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(this).use { output -> input.copyTo(output) }
                        }
                    }
                    if (displayName.lowercase().endsWith(".xapk") || displayName.lowercase().endsWith(".apks")) {
                        viewModel.openXapkFile(cacheFile)
                    } else {
                        viewModel.openApkInstallDialog(cacheFile)
                    }
                } catch (e: Exception) {
                    viewModel.showMessage("Failed to load file: ${e.localizedMessage}")
                }
            }
        }
    }

    val filteredApks = remember(storageApks, searchQuery, filterType) {
        val base = if (searchQuery.isBlank()) storageApks else storageApks.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true) ||
            it.name.contains(searchQuery, ignoreCase = true)
        }
        when (filterType) {
            "READY" -> base.filter { !it.isInstalled }
            "UPDATES" -> base.filter { it.isUpgradeCandidate }
            "INSTALLED" -> base.filter { it.isInstalled }
            "DOWNGRADE" -> base.filter { it.isDowngradeCandidate }
            else -> base
        }
    }

    Scaffold(
        modifier = modifier.testTag("app_installer_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "App Installer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MiGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "MIUI Package Engine",
                                    color = Color(0xFF059669),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${filteredApks.size} package(s) detected • 1-tap install",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isBatchMode) {
                                isBatchMode = false
                                selectedForBatch = emptySet()
                            } else {
                                viewModel.handleBackPress()
                            }
                        },
                        modifier = Modifier.testTag("installer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isBatchMode = !isBatchMode }) {
                        Icon(
                            imageVector = if (isBatchMode) Icons.Default.ChecklistRtl else Icons.Default.Checklist,
                            contentDescription = "Batch Install",
                            tint = if (isBatchMode) MiOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.loadStorageApks() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh APKs")
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = isBatchMode && selectedForBatch.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedForBatch.size} APKs selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                val targets = storageApks.filter { it.path in selectedForBatch }
                                for (target in targets) {
                                    FileOpener.installApk(context, target.file)
                                }
                                viewModel.showMessage("Invoked installer for ${targets.size} package(s)")
                                isBatchMode = false
                                selectedForBatch = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MiGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install All Selected (${selectedForBatch.size})")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Primary "Browse & Install APK" Hero Action Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MiGreen.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            pickApkLauncher.launch(
                                arrayOf(
                                    "application/vnd.android.package-archive",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MiGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Browse & Install APK File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pick any .apk, .xapk, or .apks file from Downloads, WhatsApp, Telegram, or internal storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Pick",
                            tint = Color(0xFF047857),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. Permission Banner if Install Unknown Apps is not granted
            if (!canInstallUnknown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Permission Required to Install Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Allow Mi Explorer to install apps in Android Settings to perform 1-tap installation.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow in System Settings", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // 3. Quick Navigation to APK Cloner & Downgrade Hub
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openAppManager(ApkTab.INSTALLED_APPS) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Need to Extract or Downgrade Installed Apps?",
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED)
                            )
                            Text(
                                text = "Tap here to open App Cloner & Downgrade Archive Hub",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF8B5CF6))
                    }
                }
            }

            // 4. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search APKs by app name, package, or file...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MiGreen) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 5. Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InstallerFilterChip("All (${storageApks.size})", filterType == "ALL") { filterType = "ALL" }
                    InstallerFilterChip("Ready (${storageApks.count { !it.isInstalled }})", filterType == "READY") { filterType = "READY" }
                    InstallerFilterChip("Updates (${storageApks.count { it.isUpgradeCandidate }})", filterType == "UPDATES") { filterType = "UPDATES" }
                    InstallerFilterChip("Installed (${storageApks.count { it.isInstalled }})", filterType == "INSTALLED") { filterType = "INSTALLED" }
                }
            }

            // 6. Loading or Empty State
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MiGreen)
                    }
                }
            } else if (filteredApks.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching APK packages found" else "No APK files found on storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use \"Browse & Install APK File\" above to pick any file, or transfer APKs via Mi Fast Share.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    pickApkLauncher.launch(
                                        arrayOf(
                                            "application/vnd.android.package-archive",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MiGreen)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Phone Files")
                            }
                        }
                    }
                }
            } else {
                items(filteredApks, key = { it.path }) { apk ->
                    InstallerApkCard(
                        apk = apk,
                        isBatchMode = isBatchMode,
                        isSelected = apk.path in selectedForBatch,
                        onToggleSelect = {
                            selectedForBatch = if (apk.path in selectedForBatch) {
                                selectedForBatch - apk.path
                            } else {
                                selectedForBatch + apk.path
                            }
                        },
                        onInstall = {
                            inspectingApk = apk
                        },
                        onInspect = {
                            inspectingApk = apk
                        },
                        onShare = {
                            FileOpener.shareFile(context, FileItem(apk.file))
                        },
                        onFastShare = {
                            viewModel.sendBackupViaFastShare(apk)
                        },
                        onDelete = {
                            apkToDelete = apk
                        }
                    )
                }
            }
        }
    }

    // Inspect / Full Permissions Dialog
    inspectingApk?.let { apk ->
        ApkInstallDialog(
            apk = apk,
            onDismiss = { inspectingApk = null },
            onInstall = {
                FileOpener.installApk(context, apk.file)
            },
            onShare = {
                FileOpener.shareFile(context, FileItem(apk.file))
            },
            onFastShare = {
                viewModel.sendBackupViaFastShare(apk)
            },
            onChecksum = {
                checksumTarget = FileItem(apk.file)
            }
        )
    }

    // Checksum Dialog
    checksumTarget?.let { target ->
        ChecksumDialog(
            item = target,
            onDismiss = { checksumTarget = null }
        )
    }

    // Delete APK Confirmation
    apkToDelete?.let { apk ->
        AlertDialog(
            onDismissRequest = { apkToDelete = null },
            title = { Text("Delete APK File?") },
            text = { Text("Are you sure you want to delete \"${apk.name}\"? This will remove the installation file from storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStorageApk(apk)
                        apkToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { apkToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InstallerApkCard(
    apk: ApkFileItem,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onInstall: () -> Unit,
    onInspect: () -> Unit,
    onShare: () -> Unit,
    onFastShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isBatchMode) onToggleSelect() else onInspect()
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBatchMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // App Icon
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = remember(apk.icon) {
                        try { apk.icon?.toBitmap(width = 100, height = 100)?.asImageBitmap() } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = apk.appName,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(Icons.Default.Android, contentDescription = null, tint = MiGreen, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = apk.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Status Badge
                        when {
                            apk.isUpgradeCandidate -> {
                                StatusTag("UPDATE", MiBlue)
                            }
                            apk.isDowngradeCandidate -> {
                                StatusTag("DOWNGRADE", MiOrange)
                            }
                            apk.isInstalled -> {
                                StatusTag("INSTALLED", MaterialTheme.colorScheme.outline)
                            }
                            else -> {
                                StatusTag("NEW APP", MiGreen)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "v${apk.versionName} • ${apk.packageName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${apk.formattedSize} • ${apk.targetSdkLabel} • ${apk.supportedAbis.firstOrNull() ?: "Universal"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary 1-Tap Install Button
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            apk.isDowngradeCandidate -> MiOrange
                            apk.isUpgradeCandidate -> MiBlue
                            apk.isInstalled -> Color(0xFF64748B)
                            else -> MiGreen
                        }
                    ),
                    modifier = Modifier.weight(1.3f).height(38.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (apk.isInstalled) Icons.Default.Update else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            apk.isDowngradeCandidate -> "Downgrade"
                            apk.isUpgradeCandidate -> "Update"
                            apk.isInstalled -> "Reinstall"
                            else -> "Install App"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Details / Audit Button
                OutlinedButton(
                    onClick = onInspect,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audit", style = MaterialTheme.typography.labelSmall)
                }

                // Fast Share
                IconButton(onClick = onFastShare, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.WifiTethering, contentDescription = "Fast Share", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                }

                // Share
                IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }

                // Delete
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusTag(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun InstallerFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MiGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "package.apk"
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        name = cursor.getString(idx)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    } else if (uri.path != null) {
        name = File(uri.path!!).name
    }
    return name
}
