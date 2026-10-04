package com.mi.explorer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.mi.explorer.data.model.ApkFileItem
import com.mi.explorer.ui.theme.MiGreen
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.utils.FileOpener
import com.mi.explorer.utils.InAppPackageInstallerHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class InAppInstallProgress {
    IDLE,
    STAGING,
    PROMPTING,
    COMPLETED
}

@Composable
fun ApkInstallDialog(
    apk: ApkFileItem,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onShare: () -> Unit = {},
    onFastShare: () -> Unit = {},
    onChecksum: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installProgress by remember { mutableStateOf(InAppInstallProgress.IDLE) }
    var stagingProgress by remember { mutableStateOf(0f) }
    var stagingMessage by remember { mutableStateOf("") }
    var showPermissionsList by remember { mutableStateOf(false) }

    // Check if install unknown apps permission is granted
    var canInstallUnknown by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else true
        )
    }

    // Check if app is installed on device
    val isAppInstalledOnDevice by remember(apk.packageName) {
        derivedStateOf {
            try {
                context.packageManager.getPackageInfo(apk.packageName, 0) != null
            } catch (e: Exception) {
                false
            }
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
                .heightIn(max = 680.dp)
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
                            Text("Mi Package Installer", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
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
                            imageVector = Icons.Default.Android,
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
                    apk.isDowngradeCandidate -> {
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
                                        text = "Version Downgrade (v${apk.installedVersionName} ➔ v${apk.versionName})",
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
                                        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                            data = Uri.parse("package:${apk.packageName}")
                                        }
                                        context.startActivity(intent)
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
                    apk.isInstalled -> {
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
                                        text = if (apk.isCurrentVersion) "Already Installed (Reinstall)" else "App Update (v${apk.installedVersionName} ➔ v${apk.versionName})",
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
                                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
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

                        // Sensitive permissions preview chips
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

                Spacer(modifier = Modifier.height(16.dp))

                // Installation Progress View
                if (installProgress == InAppInstallProgress.STAGING) {
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

                // Main Install Button
                Button(
                    onClick = {
                        installProgress = InAppInstallProgress.STAGING
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
                                    installProgress = InAppInstallProgress.COMPLETED
                                },
                                onFailure = {
                                    val fallback = FileOpener.installApk(context, apk.file)
                                    installProgress = if (fallback) InAppInstallProgress.COMPLETED else InAppInstallProgress.IDLE
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("in_app_install_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (apk.isDowngradeCandidate) MiOrange else MiGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (apk.isInstalled) Icons.Default.Update else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            apk.isDowngradeCandidate -> "Downgrade App"
                            apk.isInstalled -> "Update App"
                            else -> "Install Application"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // If app is currently installed or after installing: Provide Open App Button
                if (isAppInstalledOnDevice || installProgress == InAppInstallProgress.COMPLETED) {
                    Spacer(modifier = Modifier.height(8.dp))
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
