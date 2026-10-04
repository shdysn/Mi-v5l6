package com.mi.explorer.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class StatusMediaItem(
    val file: File,
    val isVideo: Boolean,
    val size: Long,
    val lastModified: Long,
    val sourceApp: String = "WhatsApp"
) {
    val formattedSize: String get() = FileItem.formatBytes(size)
}

data class SentMediaSummary(
    val totalFiles: Int,
    val totalBytes: Long,
    val files: List<File>
) {
    val formattedTotalSize: String get() = FileItem.formatBytes(totalBytes)
}

class SocialStatusRepository(private val context: Context) {

    private val root = Environment.getExternalStorageDirectory()

    private val statusDirCandidates = listOf(
        // Modern WhatsApp (Android 11+)
        File(root, "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"),
        // WhatsApp Business
        File(root, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"),
        // Legacy WhatsApp
        File(root, "WhatsApp/Media/.Statuses"),
        // GB WhatsApp
        File(root, "GBWhatsApp/Media/.Statuses")
    )

    private val savedStatusesDir = File(root, "Pictures/StatusSaver").apply {
        if (!exists()) mkdirs()
    }

    suspend fun getActiveStatuses(): List<StatusMediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StatusMediaItem>()

        for (dir in statusDirCandidates) {
            if (!dir.exists() || !dir.isDirectory) continue

            val appName = when {
                dir.absolutePath.contains("w4b") -> "WhatsApp Business"
                dir.absolutePath.contains("GBWhatsApp") -> "GBWhatsApp"
                else -> "WhatsApp"
            }

            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.length() > 0) {
                    val name = f.name.lowercase()
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                        list.add(StatusMediaItem(f, isVideo = false, f.length(), f.lastModified(), appName))
                    } else if (name.endsWith(".mp4") || name.endsWith(".3gp") || name.endsWith(".mkv")) {
                        list.add(StatusMediaItem(f, isVideo = true, f.length(), f.lastModified(), appName))
                    }
                }
            }
        }

        list.sortedByDescending { it.lastModified }
    }

    suspend fun getSavedStatuses(): List<StatusMediaItem> = withContext(Dispatchers.IO) {
        if (!savedStatusesDir.exists()) return@withContext emptyList()
        savedStatusesDir.listFiles()?.filter { it.isFile && it.length() > 0 }?.map { f ->
            val name = f.name.lowercase()
            val isVid = name.endsWith(".mp4") || name.endsWith(".mkv")
            StatusMediaItem(f, isVideo = isVid, f.length(), f.lastModified(), "Saved")
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }

    suspend fun saveStatusToGallery(statusFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!savedStatusesDir.exists()) savedStatusesDir.mkdirs()

            val ext = statusFile.name.substringAfterLast('.', "jpg")
            val destFile = File(savedStatusesDir, "status_${System.currentTimeMillis()}.$ext")

            FileInputStream(statusFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Broadcast to Android Gallery
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(if (destFile.name.endsWith(".mp4")) "video/mp4" else "image/jpeg"),
                null
            )

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanSentMedia(): SentMediaSummary = withContext(Dispatchers.IO) {
        val sentDirs = listOf(
            File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent"),
            File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Sent"),
            File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/Sent"),
            File(root, "WhatsApp/Media/WhatsApp Images/Sent"),
            File(root, "WhatsApp/Media/WhatsApp Video/Sent")
        )

        val files = mutableListOf<File>()
        var totalBytes = 0L

        sentDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.length() > 0) {
                        files.add(f)
                        totalBytes += f.length()
                    }
                }
            }
        }

        SentMediaSummary(files.size, totalBytes, files)
    }

    suspend fun cleanSentMedia(files: List<File>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        files.forEach { f ->
            if (f.delete()) deleted++
        }
        deleted
    }
}
