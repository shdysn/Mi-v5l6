package com.mi.explorer.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.mi.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object InAppPackageInstallerHelper {

    /**
     * Installs a standard APK file using Android's native PackageInstaller Session API.
     * Streams APK bytes directly in-app, providing live progress feedback.
     */
    suspend fun installApkSession(
        context: Context,
        apkFile: File,
        packageName: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || !apkFile.canRead()) {
            return@withContext Result.failure(Exception("APK file not accessible"))
        }

        var session: PackageInstaller.Session? = null
        try {
            onProgress(0.05f, "Initializing package installer session...")
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (!packageName.isNullOrBlank()) {
                try {
                    params.setAppPackageName(packageName)
                } catch (ignored: Exception) {}
            }

            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            val totalSize = apkFile.length()
            onProgress(0.15f, "Staging package (${FileItem.formatBytes(totalSize)})...")

            session.openWrite("base.apk", 0, totalSize).use { sessionOut ->
                FileInputStream(apkFile).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        sessionOut.write(buffer, 0, count)
                        bytesCopied += count
                        val progress = 0.15f + (0.75f * bytesCopied / totalSize)
                        onProgress(progress, "Staging: ${(progress * 100).toInt()}%")
                    }
                }
                session.fsync(sessionOut)
            }

            onProgress(0.95f, "Committing installation...")

            val intent = Intent(context, com.mi.explorer.MainActivity::class.java).apply {
                action = "com.mi.explorer.ACTION_INSTALL_COMMIT"
                putExtra("package_name", packageName)
                putExtra("session_id", sessionId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                sessionId,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            session = null

            onProgress(1.0f, "Installation session submitted")
            Result.success(true)
        } catch (e: Exception) {
            try {
                session?.abandon()
            } catch (ignored: Exception) {}

            // Fallback to system package manager intent if session fails
            try {
                val fallbackLaunched = FileOpener.installApk(context, apkFile)
                if (fallbackLaunched) {
                    Result.success(true)
                } else {
                    Result.failure(e)
                }
            } catch (err: Exception) {
                Result.failure(e)
            }
        }
    }
}
