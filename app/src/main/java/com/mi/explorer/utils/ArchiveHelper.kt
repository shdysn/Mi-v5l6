package com.mi.explorer.utils

import com.mi.explorer.data.repository.ZipArchiveInfo
import com.mi.explorer.data.repository.ZipEntryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

enum class ArchiveType {
    ZIP,
    TAR,
    GZ,
    TAR_GZ,
    SEVEN_ZIP,
    RAR,
    UNKNOWN
}

object ArchiveHelper {

    fun detectArchiveType(file: File): ArchiveType {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> ArchiveType.TAR_GZ
            name.endsWith(".tar") -> ArchiveType.TAR
            name.endsWith(".gz") -> ArchiveType.GZ
            name.endsWith(".7z") -> ArchiveType.SEVEN_ZIP
            name.endsWith(".rar") -> ArchiveType.RAR
            name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".apk") || name.endsWith(".xapk") || name.endsWith(".apks") -> ArchiveType.ZIP
            else -> {
                // Header magic bytes check
                try {
                    FileInputStream(file).use { input ->
                        val bytes = ByteArray(7)
                        val count = input.read(bytes)
                        if (count >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                            return ArchiveType.GZ
                        }
                        if (count >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
                            return ArchiveType.ZIP
                        }
                        if (count >= 6 && bytes[0] == '7'.code.toByte() && bytes[1] == 'z'.code.toByte() && bytes[2] == 0xBC.toByte()) {
                            return ArchiveType.SEVEN_ZIP
                        }
                        if (count >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'a'.code.toByte() && bytes[2] == 'r'.code.toByte() && bytes[3] == '!'.code.toByte()) {
                            return ArchiveType.RAR
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                ArchiveType.UNKNOWN
            }
        }
    }

    suspend fun inspectArchive(file: File): Result<ZipArchiveInfo> = withContext(Dispatchers.IO) {
        val type = detectArchiveType(file)
        try {
            when (type) {
                ArchiveType.ZIP -> inspectZip(file)
                ArchiveType.TAR -> inspectTar(file, isGzipped = false)
                ArchiveType.TAR_GZ -> inspectTar(file, isGzipped = true)
                ArchiveType.GZ -> inspectGz(file)
                ArchiveType.SEVEN_ZIP -> inspect7zOrRar(file, "7Z Archive")
                ArchiveType.RAR -> inspect7zOrRar(file, "RAR Archive")
                ArchiveType.UNKNOWN -> inspectZip(file)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun inspectZip(file: File): Result<ZipArchiveInfo> {
        val entriesList = mutableListOf<ZipEntryItem>()
        var totalUncompressed = 0L

        ZipFile(file).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val itemName = entry.name.trimEnd('/').substringAfterLast('/')
                entriesList.add(
                    ZipEntryItem(
                        name = if (itemName.isEmpty()) entry.name else itemName,
                        fullPath = entry.name,
                        size = entry.size.coerceAtLeast(0L),
                        compressedSize = entry.compressedSize.coerceAtLeast(0L),
                        isDirectory = entry.isDirectory,
                        lastModified = entry.time
                    )
                )
                if (!entry.isDirectory && entry.size > 0) {
                    totalUncompressed += entry.size
                }
            }
        }

        return Result.success(
            ZipArchiveInfo(
                sourceFile = file,
                sourceUri = null,
                archiveName = file.name,
                totalEntries = entriesList.size,
                totalUncompressedBytes = totalUncompressed,
                totalArchiveBytes = file.length(),
                entries = entriesList.sortedWith(compareBy({ !it.isDirectory }, { it.fullPath }))
            )
        )
    }

    private fun inspectTar(file: File, isGzipped: Boolean): Result<ZipArchiveInfo> {
        val entriesList = mutableListOf<ZipEntryItem>()
        var totalUncompressed = 0L

        val rawInput = FileInputStream(file)
        val stream: InputStream = if (isGzipped) GZIPInputStream(rawInput) else rawInput

        BufferedInputStream(stream).use { bis ->
            val header = ByteArray(512)
            while (true) {
                val read = readFully(bis, header)
                if (read < 512 || isAllZeros(header)) break

                // Tar file name (first 100 bytes)
                val rawName = String(header, 0, 100).trim { it <= ' ' || it == '\u0000' }
                if (rawName.isEmpty()) continue

                // Size is stored as octal string from offset 124, length 12
                val sizeStr = String(header, 124, 12).trim { it <= ' ' || it == '\u0000' }
                val size = try { sizeStr.toLong(8) } catch (e: Exception) { 0L }

                val typeFlag = header[156].toInt().toChar()
                val isDir = typeFlag == '5' || rawName.endsWith('/')

                val itemName = rawName.trimEnd('/').substringAfterLast('/')
                entriesList.add(
                    ZipEntryItem(
                        name = if (itemName.isEmpty()) rawName else itemName,
                        fullPath = rawName,
                        size = size,
                        compressedSize = (size * 0.7).toLong(),
                        isDirectory = isDir,
                        lastModified = file.lastModified()
                    )
                )

                if (!isDir) totalUncompressed += size

                // Skip file body padded to 512 boundary
                val skipBytes = ((size + 511) / 512) * 512
                var skippedTotal = 0L
                while (skippedTotal < skipBytes) {
                    val skipped = bis.skip(skipBytes - skippedTotal)
                    if (skipped <= 0) {
                        // fallback read
                        val dummy = ByteArray(minOf(4096, (skipBytes - skippedTotal).toInt()))
                        val r = bis.read(dummy)
                        if (r <= 0) break
                        skippedTotal += r
                    } else {
                        skippedTotal += skipped
                    }
                }
            }
        }

        return Result.success(
            ZipArchiveInfo(
                sourceFile = file,
                sourceUri = null,
                archiveName = file.name,
                totalEntries = entriesList.size,
                totalUncompressedBytes = totalUncompressed,
                totalArchiveBytes = file.length(),
                entries = entriesList.sortedWith(compareBy({ !it.isDirectory }, { it.fullPath }))
            )
        )
    }

    private fun inspectGz(file: File): Result<ZipArchiveInfo> {
        val entryName = file.name.removeSuffix(".gz")
        val size = file.length() * 2 // estimated
        val entriesList = listOf(
            ZipEntryItem(
                name = entryName,
                fullPath = entryName,
                size = size,
                compressedSize = file.length(),
                isDirectory = false,
                lastModified = file.lastModified()
            )
        )
        return Result.success(
            ZipArchiveInfo(
                sourceFile = file,
                sourceUri = null,
                archiveName = file.name,
                totalEntries = 1,
                totalUncompressedBytes = size,
                totalArchiveBytes = file.length(),
                entries = entriesList
            )
        )
    }

    private fun inspect7zOrRar(file: File, typeLabel: String): Result<ZipArchiveInfo> {
        val entriesList = listOf(
            ZipEntryItem(
                name = "[$typeLabel Content]",
                fullPath = file.nameWithoutExtension,
                size = file.length() * 2,
                compressedSize = file.length(),
                isDirectory = true,
                lastModified = file.lastModified()
            )
        )
        return Result.success(
            ZipArchiveInfo(
                sourceFile = file,
                sourceUri = null,
                archiveName = file.name,
                totalEntries = 1,
                totalUncompressedBytes = file.length() * 2,
                totalArchiveBytes = file.length(),
                entries = entriesList
            )
        )
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count < 0) break
            total += count
        }
        return total
    }

    private fun isAllZeros(bytes: ByteArray): Boolean {
        for (b in bytes) {
            if (b != 0.toByte()) return false
        }
        return true
    }

    suspend fun extractArchive(
        file: File,
        destDir: File,
        password: String? = null,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!destDir.exists()) destDir.mkdirs()
            val type = detectArchiveType(file)

            when (type) {
                ArchiveType.ZIP -> extractZip(file, destDir, onProgress)
                ArchiveType.TAR -> extractTar(file, destDir, isGzipped = false, onProgress)
                ArchiveType.TAR_GZ -> extractTar(file, destDir, isGzipped = true, onProgress)
                ArchiveType.GZ -> extractGz(file, destDir, onProgress)
                ArchiveType.SEVEN_ZIP, ArchiveType.RAR -> {
                    Result.failure(UnsupportedOperationException("Native extraction for ${file.extension.uppercase()} is not supported. Use 'Open With' to open with an external archiver."))
                }
                ArchiveType.UNKNOWN -> extractZip(file, destDir, onProgress)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractZip(file: File, destDir: File, onProgress: (Float, String) -> Unit): Result<File> {
        ZipFile(file).use { zf ->
            val total = zf.size()
            var current = 0
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(destDir, entry.name)
                current++
                onProgress(current.toFloat() / total.toFloat(), entry.name)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        return Result.success(destDir)
    }

    private fun extractTar(file: File, destDir: File, isGzipped: Boolean, onProgress: (Float, String) -> Unit): Result<File> {
        val rawInput = FileInputStream(file)
        val stream: InputStream = if (isGzipped) GZIPInputStream(rawInput) else rawInput

        BufferedInputStream(stream).use { bis ->
            val header = ByteArray(512)
            var count = 0
            while (true) {
                val read = readFully(bis, header)
                if (read < 512 || isAllZeros(header)) break

                val rawName = String(header, 0, 100).trim { it <= ' ' || it == '\u0000' }
                if (rawName.isEmpty()) continue

                val sizeStr = String(header, 124, 12).trim { it <= ' ' || it == '\u0000' }
                val size = try { sizeStr.toLong(8) } catch (e: Exception) { 0L }

                val typeFlag = header[156].toInt().toChar()
                val isDir = typeFlag == '5' || rawName.endsWith('/')

                val outFile = File(destDir, rawName)
                count++
                onProgress(0.5f, rawName)

                if (isDir) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        var remaining = size
                        val buf = ByteArray(4096)
                        while (remaining > 0) {
                            val r = bis.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                            remaining -= r
                        }
                    }
                }

                // Skip padding to 512 boundary
                val padding = (512 - (size % 512)) % 512
                if (padding > 0) {
                    bis.skip(padding)
                }
            }
        }
        return Result.success(destDir)
    }

    private fun extractGz(file: File, destDir: File, onProgress: (Float, String) -> Unit): Result<File> {
        val outFileName = file.name.removeSuffix(".gz")
        val outFile = File(destDir, outFileName)
        onProgress(0.1f, outFileName)
        GZIPInputStream(FileInputStream(file)).use { gzIn ->
            FileOutputStream(outFile).use { fos ->
                gzIn.copyTo(fos)
            }
        }
        onProgress(1f, outFileName)
        return Result.success(destDir)
    }
}
