package com.mi.explorer.data.repository

import android.content.Context
import android.os.Environment
import com.mi.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class DuplicateGroup(
    val original: FileItem,
    val duplicates: List<FileItem>
) {
    val wastedBytes: Long get() = duplicates.sumOf { it.size }
    val totalFiles: Int get() = duplicates.size + 1
}

data class DuplicateScanResult(
    val groups: List<DuplicateGroup>,
    val totalWastedBytes: Long
)

class DuplicateRepository(private val context: Context) {

    suspend fun findDuplicates(): DuplicateScanResult = withContext(Dispatchers.IO) {
        val searchDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            File(context.filesDir, "MiExplorer")
        ).filter { it.exists() && it.canRead() }

        val allFiles = mutableListOf<File>()
        for (dir in searchDirs) {
            collectFiles(dir, allFiles, maxFiles = 1000, depth = 0, maxDepth = 3)
        }

        // Group 1: Group by file size (> 10KB)
        val sizeMap = allFiles
            .filter { it.length() > 10 * 1024L }
            .groupBy { it.length() }
            .filter { it.value.size > 1 }

        val duplicateGroups = mutableListOf<DuplicateGroup>()

        // Group 2: For items with same size, compute partial hash (fast)
        for ((_, candidateFiles) in sizeMap) {
            val hashMap = mutableMapOf<String, MutableList<File>>()
            for (file in candidateFiles) {
                val hash = getPartialHash(file)
                if (hash != null) {
                    hashMap.getOrPut(hash) { mutableListOf() }.add(file)
                }
            }

            for ((_, matchingFiles) in hashMap) {
                if (matchingFiles.size > 1) {
                    // Sort by last modified: oldest is considered original
                    val sorted = matchingFiles.sortedBy { it.lastModified() }
                    val original = FileItem(sorted.first())
                    val dupes = sorted.drop(1).map { FileItem(it) }
                    duplicateGroups.add(DuplicateGroup(original, dupes))
                }
            }
        }

        val totalWasted = duplicateGroups.sumOf { it.wastedBytes }
        DuplicateScanResult(
            groups = duplicateGroups.sortedByDescending { it.wastedBytes },
            totalWastedBytes = totalWasted
        )
    }

    private fun collectFiles(dir: File, result: MutableList<File>, maxFiles: Int, depth: Int, maxDepth: Int) {
        if (depth > maxDepth || result.size >= maxFiles) return
        if (dir.name.startsWith(".") || dir.name.equals("Android", ignoreCase = true)) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                collectFiles(f, result, maxFiles, depth + 1, maxDepth)
            } else {
                result.add(f)
            }
        }
    }

    private fun getPartialHash(file: File): String? {
        return try {
            val length = file.length()
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(16 * 1024) // 16KB head
                val read = input.read(buffer)
                if (read > 0) md.update(buffer, 0, read)
            }
            // Include file length in hash
            md.digest().joinToString("") { "%02x".format(it) } + "_$length"
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteFiles(files: List<FileItem>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (item in files) {
            try {
                if (item.file.delete()) count++
            } catch (e: Exception) {
                // ignore
            }
        }
        count
    }
}
