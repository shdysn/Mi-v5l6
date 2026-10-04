package com.mi.explorer.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class XapkInfo(
    val file: File,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val iconBitmap: Bitmap?,
    val splitApkNames: List<String>,
    val obbFileNames: List<String>,
    val totalSizeBytes: Long
)

object XapkInstaller {

    suspend fun parseXapk(file: File): Result<XapkInfo> = withContext(Dispatchers.IO) {
        try {
            var packageName = ""
            var appName = file.nameWithoutExtension
            var versionName = "1.0"
            var versionCode = 1L
            var iconBitmap: Bitmap? = null
            val apkNames = mutableListOf<String>()
            val obbNames = mutableListOf<String>()

            ZipFile(file).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.lowercase()
                    if (name.endsWith(".apk")) {
                        apkNames.add(entry.name)
                    } else if (name.endsWith(".obb")) {
                        obbNames.add(entry.name)
                    } else if (name == "manifest.json" || name.endsWith("/manifest.json")) {
                        try {
                            zf.getInputStream(entry).use { stream ->
                                val jsonStr = stream.bufferedReader().readText()
                                val obj = JSONObject(jsonStr)
                                packageName = obj.optString("package_name", "")
                                appName = obj.optString("name", appName)
                                versionName = obj.optString("version_name", versionName)
                                versionCode = obj.optLong("version_code", versionCode)
                            }
                        } catch (e: Exception) {
                            // ignore json error
                        }
                    } else if (name == "icon.png" || name.endsWith("/icon.png")) {
                        try {
                            zf.getInputStream(entry).use { stream ->
                                iconBitmap = BitmapFactory.decodeStream(stream)
                            }
                        } catch (e: Exception) {
                            // ignore icon error
                        }
                    }
                }
            }

            if (apkNames.isEmpty()) {
                return@withContext Result.failure(Exception("No APK files found inside ${file.name}"))
            }

            if (packageName.isEmpty()) {
                packageName = file.nameWithoutExtension.lowercase().replace(" ", ".")
            }

            Result.success(
                XapkInfo(
                    file = file,
                    packageName = packageName,
                    appName = appName,
                    versionName = versionName,
                    versionCode = versionCode,
                    iconBitmap = iconBitmap,
                    splitApkNames = apkNames,
                    obbFileNames = obbNames,
                    totalSizeBytes = file.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installXapk(
        context: Context,
        xapkInfo: XapkInfo,
        onProgress: (Float, String) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Extract OBB files if any
            if (xapkInfo.obbFileNames.isNotEmpty()) {
                val obbDir = File(Environment.getExternalStorageDirectory(), "Android/obb/${xapkInfo.packageName}")
                obbDir.mkdirs()
                ZipFile(xapkInfo.file).use { zf ->
                    for (obbEntryName in xapkInfo.obbFileNames) {
                        val entry = zf.getEntry(obbEntryName) ?: continue
                        val obbFile = File(obbDir, obbEntryName.substringAfterLast('/'))
                        onProgress(0.1f, "Copying OBB: ${obbFile.name}")
                        zf.getInputStream(entry).use { input ->
                            FileOutputStream(obbFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            // Step 2: Session-based PackageInstaller for Split APKs
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (xapkInfo.packageName.isNotEmpty()) {
                params.setAppPackageName(xapkInfo.packageName)
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            try {
                ZipFile(xapkInfo.file).use { zf ->
                    val totalApks = xapkInfo.splitApkNames.size
                    for ((index, apkEntryName) in xapkInfo.splitApkNames.withIndex() ) {
                        val entry = zf.getEntry(apkEntryName) ?: continue
                        val cleanName = apkEntryName.substringAfterLast('/').replace("[^a-zA-Z0-9._]".toRegex(), "_")
                        onProgress(0.2f + (0.7f * index / totalApks), "Staging split: $cleanName")

                        session.openWrite(cleanName, 0, entry.size).use { sessionOut ->
                            zf.getInputStream(entry).use { apkIn ->
                                apkIn.copyTo(sessionOut)
                            }
                            session.fsync(sessionOut)
                        }
                    }
                }

                // Step 3: Commit session
                onProgress(0.95f, "Finalizing installation...")
                val intent = Intent(context, com.mi.explorer.MainActivity::class.java).apply {
                    action = "com.mi.explorer.ACTION_INSTALL_COMMIT"
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
                Result.success(true)
            } catch (e: Exception) {
                session.abandon()
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
