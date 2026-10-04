package com.mi.explorer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.explorer.data.model.ApkFileItem
import com.mi.explorer.ui.theme.MiBlue
import com.mi.explorer.ui.theme.MiGreen
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.utils.FileOpener
import com.mi.explorer.utils.InAppPackageInstallerHelper
import com.mi.explorer.utils.InstallSessionEvent
import com.mi.explorer.utils.InstallerStatusBus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class InAppInstallProgress {
    IDLE,
    STAGING,
    PROMPTING,
    COMPLETED,
    FAILED
}

private fun queryInstalledPackage(context: Context, packageName: String): PackageInfo? {
    if (packageName.isBlank() || packageName == "Unknown") return null
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (_: Exception) {
        null
    }
}

private fun getPkgVersionCode(pkg: PackageInfo?): Long {
    if (pkg == null) return 0L
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkg.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pkg.versionCode.toLong()
    }
}

@Composable
fun ApkInstallDialog(
    apk: ApkFileItem,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onShare: () -> Unit = {},
    onFastShare: () -> Unit = {},
    onChecksum: () -> Unit = {},
    autoStartInstall: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var installProgress by remember(apk.path) { mutableStateOf(InAppInstallProgress.IDLE) }
    var stagingProgress by remember(apk.path) { mutableFloatStateOf(0f) }
    var stagingMessage by remember(apk.path) { mutableStateOf("") }
    var errorMessage by remember(apk.path) { mutableStateOf<String?>(null) }
    var pendingConfirmIntent by remember(apk.path) { mutableStateOf<Intent?>(null) }
    var showPermissionsList by remember { mutableStateOf(false) }

    var canInstallUnknown by remember {
        mutableStateOf(FileOpener.canInstallUnknownApps(context))
    }

    var installedPkgInfo by remember(apk.packageName) {
        mutableStateOf(queryInstalledPackage(context, apk.packageName))
    }

    val isAppInstalledOnDevice = installedPkgInfo != null
    val liveInstalledVersionCode = getPkgVersionCode(installedPkgInfo)
    val liveInstalledVersionName = installedPkgInfo?.versionName ?: apk.installedVersionName
    val isLiveDowngrade = isAppInstalledOnDevice && apk.versionCode > 0 && liveInstalledVersionCode > apk.versionCode
    val isLiveCurrentVersion = isAppInstalledOnDevice && apk.versionCode > 0 && liveInstalledVersionCode == apk.versionCode
    val isSplitBundle = remember(apk.file.name) {
        val ext = apk.file.extension.lowercase()
        ext == "xapk" || ext == "apks"
    }

    fun startInstallation() {
        if (!FileOpener.canInstallUnknownApps(context)) {
            canInstallUnknown = false
            errorMessage = "Please allow 'Install unknown apps' permission in Settings first, then tap Install."
            FileOpener.requestInstallUnknownAppsPermission(context)
            return
        }
        errorMessage = null
        installProgress = InAppInstallProgress.STAGING
        stagingProgress = 0.05f
        stagingMessage = "Preparing package installation..."

        scope.launch {
            val res = InAppPackageInstallerHelper.installApkSession(
                context = context,
                apkFile = apk.file,
                packageName = apk.packageName
            ) { progress, message ->
                stagingProgress = progress
                stagingMessage = message
            }
            res.fold(
                onSuccess = {
                    if (installProgress == InAppInstallProgress.STAGING) {
                        installProgress = InAppInstallProgress.PROMPTING
                        stagingMessage = "System installer opened — confirm on screen to finish."
                    }
                },
                onFailure = { err ->
                    installProgress = InAppInstallProgress.FAILED
                    errorMessage = err.localizedMessage ?: "Failed to stage APK package"
                }
            )
        }
    }

    // Listen to PackageInstallerStatusReceiver callbacks
    LaunchedEffect(apk.path) {
        InstallerStatusBus.events.collectLatest { event ->
            when (event) {
                is InstallSessionEvent.PendingUserAction -> {
                    pendingConfirmIntent = event.confirmIntent
                    installProgress = InAppInstallProgress.PROMPTING
                    stagingMessage = "Confirm installation in the Android dialog..."
                }
                is InstallSessionEvent.Success -> {
                    installedPkgInfo = queryInstalledPackage(context, apk.packageName)
                    installProgress = InAppInstallProgress.COMPLETED
                    stagingMessage = "Application installed successfully!"
                    errorMessage = null
                }
                is InstallSessionEvent.Cancelled -> {
                    installProgress = InAppInstallProgress.IDLE
                    errorMessage = "Installation cancelled by user."
                }
                is InstallSessionEvent.Failed -> {
                    installProgress = InAppInstallProgress.FAILED
                    errorMessage = event.reason
                }
            }
        }
    }

    // Refresh permission and installed status whenever returning from System Settings or System Installer
    DisposableEffect(lifecycleOwner, apk.packageName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowCanInstall = FileOpener.canInstallUnknownApps(context)
                canInstallUnknown = nowCanInstall
                val updatedPkg = queryInstalledPackage(context, apk.packageName)
                val updatedVerCode = getPkgVersionCode(updatedPkg)
                installedPkgInfo = updatedPkg

                if (installProgress == InAppInstallProgress.PROMPTING && updatedPkg != null) {
                    if (apk.versionCode <= 0L || updatedVerCode == apk.versionCode) {
                        installProgress = InAppInstallProgress.COMPLETED
                        stagingMessage = "Application installed successfully!"
                        errorMessage = null
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-start installation if requested by 1-tap Install button
    LaunchedEffect(apk.path, autoStartInstall) {
        if (autoStartInstall && canInstallUnknown && !isLiveDowngrade) {
            startInstallation()
        }
    }

    Dialog(
        onDismissRequest = {
            if (installProgress != InAppInstallProgress.STAGING) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 700.dp)
                .testTag("in_app_package_installer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = MiGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSplitBundle) "Mi Split APK Installer" else "Mi Package Installer",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MiGreen.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = remember(apk.icon) {
                        try {
                            apk.icon?.toBitmap(width = 160, height = 160)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = apk.appName,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )
                    } else {
                        Icon(
                            imageVector = if (isSplitBundle) Icons.Default.Layers else Icons.Default.Android,
                            contentDescription = null,
                            tint = MiGreen,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // App Name
                Text(
                    text = apk.appName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Package & Version Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Package Name", apk.packageName))
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "v${apk.versionName} • ${apk.packageName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Installation Status Banner
                when {
                    isLiveDowngrade -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiOrange.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MiOrange, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Version Downgrade (v${liveInstalledVersionName ?: "Current"} ➔ v${apk.versionName})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MiOrange
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Android OS requires uninstalling the currently installed newer version before an older APK can be installed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        FileOpener.uninstallApp(context, apk.packageName)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Uninstall Current Version First", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    isAppInstalledOnDevice -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiGreen.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Update, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isLiveCurrentVersion) "Already Installed (Reinstall)" else "App Update (v${liveInstalledVersionName ?: "1.0"} ➔ v${apk.versionName})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                    Text(
                                        text = "Existing app data and settings will be preserved.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.NewReleases, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "New Application",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3B82F6)
                                    )
                                    Text(
                                        text = "Ready to install on this device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Unknown Apps Permission Prompt Banner
                if (!canInstallUnknown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Install Unknown Apps Permission Needed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "To install APKs directly, allow Mi Explorer in system settings.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    FileOpener.requestInstallUnknownAppsPermission(context)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow Permission in Settings", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Technical Compatibility Badges (SDK, Architecture, Size)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TARGET OS", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(apk.targetSdkLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ARCHITECTURE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(apk.supportedAbis.firstOrNull() ?: "Universal", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SIZE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(apk.formattedSize, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Permissions & Privacy Audit Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPermissionsList = !showPermissionsList },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = MiGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Permissions (${apk.permissions.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (showPermissionsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle"
                            )
                        }

                        if (apk.dangerousPermissions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                apk.dangerousPermissions.take(3).forEach { perm ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = perm,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (apk.dangerousPermissions.size > 3) {
                                    Text(
                                        text = "+${apk.dangerousPermissions.size - 3} more",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = showPermissionsList) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                if (apk.permissions.isEmpty()) {
                                    Text("No special permissions requested.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    apk.permissions.forEach { perm ->
                                        Text(
                                            text = "• ${perm.substringAfterLast('.')}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Installation Progress / Prompting / Error / Success View
                when (installProgress) {
                    InAppInstallProgress.STAGING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { stagingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MiGreen
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stagingMessage.ifEmpty { "Staging package in session..." },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MiGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    InAppInstallProgress.PROMPTING -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiBlue.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MiBlue
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stagingMessage.ifEmpty { "Waiting for confirmation in system dialog..." },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiBlue
                                    )
                                }
                                if (!isSplitBundle) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            val confirm = pendingConfirmIntent
                                            if (confirm != null) {
                                                InstallerStatusBus.launchConfirmationIntent(context, confirm)
                                            } else {
                                                FileOpener.installApk(context, apk.file)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Didn't see prompt? Open System Installer", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    InAppInstallProgress.COMPLETED -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiGreen.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Package installed successfully!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    InAppInstallProgress.FAILED -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Installation Could Not Complete",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMessage ?: "Unknown installation error",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!isSplitBundle) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            onInstall()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Install with Standard System Installer", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    InAppInstallProgress.IDLE -> {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MiOrange,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                // Main Install Button
                Button(
                    onClick = { startInstallation() },
                    enabled = installProgress != InAppInstallProgress.STAGING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("in_app_install_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLiveDowngrade) MiOrange else MiGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isAppInstalledOnDevice) Icons.Default.Update else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            installProgress == InAppInstallProgress.STAGING -> "Staging Package..."
                            isLiveDowngrade -> "Downgrade App"
                            isLiveCurrentVersion -> "Reinstall App"
                            isAppInstalledOnDevice -> "Update App"
                            isSplitBundle -> "Install Split Bundle"
                            else -> "Install Application"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Direct System Installer alternative button for standard .apk files
                if (!isSplitBundle && installProgress == InAppInstallProgress.IDLE) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = { onInstall() },
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Use Android System Installer Directly",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // If app is currently installed on device: Provide Open App Button
                if (isAppInstalledOnDevice && apk.packageName != context.packageName) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage(apk.packageName)
                            if (intent != null) {
                                context.startActivity(intent)
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Installed App", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Row: Share, Fast Share, Checksum
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onShare()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onFastShare()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fast Share", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onChecksum()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MiOrange, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hash", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
