package com.mi.explorer.utils

import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class RootStatus(
    val isSuAvailable: Boolean,
    val isRootGranted: Boolean,
    val rootUid: String? = null,
    val suPath: String? = null
)

data class RootFileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val permissions: String,
    val owner: String = "root"
)

object RootHelper {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/data/local/xbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/bin/su"
    )

    fun findSuBinary(): String? {
        for (path in SU_PATHS) {
            val f = File(path)
            if (f.exists() && f.canExecute()) {
                return path
            }
        }
        return null
    }

    suspend fun checkRootStatus(): RootStatus = withContext(Dispatchers.IO) {
        val suBinary = findSuBinary()
        var pathSu: String? = suBinary
        if (pathSu == null) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("which", "su"))
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                val line = reader.readLine()
                proc.waitFor()
                if (!line.isNullOrBlank() && File(line.trim()).exists()) {
                    pathSu = line.trim()
                }
            } catch (ignored: Exception) {}

            if (pathSu == null) {
                return@withContext RootStatus(isSuAvailable = false, isRootGranted = false)
            }
        }

        val effectiveSu = pathSu ?: "su"
        try {
            val proc = Runtime.getRuntime().exec(arrayOf(effectiveSu, "-c", "id"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val output = reader.readLine()
            proc.waitFor()
            val isGranted = output != null && output.contains("uid=0(root)")
            RootStatus(
                isSuAvailable = true,
                isRootGranted = isGranted,
                rootUid = if (isGranted) output else null,
                suPath = effectiveSu
            )
        } catch (e: Exception) {
            RootStatus(isSuAvailable = true, isRootGranted = false, suPath = effectiveSu)
        }
    }

    /**
     * Reads genuine POSIX mode bits via android.system.Os.stat()
     */
    fun getFileLinuxPermissions(file: File): String {
        return try {
            val stat = Os.lstat(file.absolutePath)
            val mode = stat.st_mode
            buildString {
                append(
                    when {
                        OsConstants.S_ISDIR(mode) -> "d"
                        OsConstants.S_ISLNK(mode) -> "l"
                        OsConstants.S_ISCHR(mode) -> "c"
                        OsConstants.S_ISBLK(mode) -> "b"
                        OsConstants.S_ISFIFO(mode) -> "p"
                        OsConstants.S_ISSOCK(mode) -> "s"
                        else -> "-"
                    }
                )
                // Owner
                append(if ((mode and OsConstants.S_IRUSR) != 0) "r" else "-")
                append(if ((mode and OsConstants.S_IWUSR) != 0) "w" else "-")
                append(
                    when {
                        (mode and OsConstants.S_ISUID) != 0 -> if ((mode and OsConstants.S_IXUSR) != 0) "s" else "S"
                        (mode and OsConstants.S_IXUSR) != 0 -> "x"
                        else -> "-"
                    }
                )
                // Group
                append(if ((mode and OsConstants.S_IRGRP) != 0) "r" else "-")
                append(if ((mode and OsConstants.S_IWGRP) != 0) "w" else "-")
                append(
                    when {
                        (mode and OsConstants.S_ISGID) != 0 -> if ((mode and OsConstants.S_IXGRP) != 0) "s" else "S"
                        (mode and OsConstants.S_IXGRP) != 0 -> "x"
                        else -> "-"
                    }
                )
                // Others
                append(if ((mode and OsConstants.S_IROTH) != 0) "r" else "-")
                append(if ((mode and OsConstants.S_IWOTH) != 0) "w" else "-")
                append(
                    when {
                        (mode and OsConstants.S_ISVTX) != 0 -> if ((mode and OsConstants.S_IXOTH) != 0) "t" else "T"
                        (mode and OsConstants.S_IXOTH) != 0 -> "x"
                        else -> "-"
                    }
                )
            }
        } catch (_: Exception) {
            buildString {
                append(if (file.isDirectory) "d" else "-")
                append(if (file.canRead()) "r" else "-")
                append(if (file.canWrite()) "w" else "-")
                append(if (file.canExecute()) "x" else "-")
                append("------")
            }
        }
    }

    /**
     * Lists directory using standard File API first; if null (permission denied),
     * falls back to executing `su -c "ls -la '<path>'"` when root is granted.
     */
    suspend fun listDirectory(dir: File, rootStatus: RootStatus?): Result<List<RootFileEntry>> = withContext(Dispatchers.IO) {
        val standardList = dir.listFiles()
        if (standardList != null) {
            val entries = standardList.map { f ->
                val perms = getFileLinuxPermissions(f)
                val size = try {
                    if (f.isDirectory) 0L else Os.stat(f.absolutePath).st_size
                } catch (_: Exception) {
                    f.length()
                }
                RootFileEntry(
                    file = f,
                    name = f.name,
                    isDirectory = f.isDirectory,
                    size = size,
                    permissions = perms
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            return@withContext Result.success(entries)
        }

        // Standard listFiles() returned null (protected partition). Try su -c ls -la if available.
        val suBin = rootStatus?.suPath ?: findSuBinary() ?: "su"
        if (rootStatus?.isRootGranted == true || rootStatus?.isSuAvailable == true) {
            try {
                val escapedPath = dir.absolutePath.replace("'", "'\\''")
                val proc = Runtime.getRuntime().exec(arrayOf(suBin, "-c", "ls -la '$escapedPath'"))
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                val parsed = mutableListOf<RootFileEntry>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line!!.trim()
                    if (raw.isEmpty() || raw.startsWith("total ")) continue
                    // Typical Android toybox ls -la output:
                    // drwxrwx--x 45 system system 4096 2026-05-01 12:00 data
                    val tokens = raw.split(Regex("\\s+"))
                    if (tokens.size >= 7 && tokens[0].length >= 10) {
                        val perms = tokens[0].take(10)
                        val isDir = perms.startsWith("d")
                        val owner = tokens.getOrNull(2) ?: "root"
                        val size = tokens.getOrNull(4)?.toLongOrNull() ?: 0L
                        // Name is after date/time token
                        val rawName = tokens.drop(7).joinToString(" ").ifEmpty { tokens.last() }
                        val cleanName = rawName.substringBefore(" -> ").trim()
                        if (cleanName == "." || cleanName == ".." || cleanName.isEmpty()) continue
                        val childFile = File(dir, cleanName)
                        parsed.add(
                            RootFileEntry(
                                file = childFile,
                                name = cleanName,
                                isDirectory = isDir,
                                size = size,
                                permissions = perms,
                                owner = owner
                            )
                        )
                    }
                }
                val exit = proc.waitFor()
                if (exit == 0 || parsed.isNotEmpty()) {
                    return@withContext Result.success(
                        parsed.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    )
                }
            } catch (_: Exception) {}
        }

        Result.failure(SecurityException("Permission denied for ${dir.absolutePath}"))
    }

    /**
     * Reads text content of a root-protected file using `su -c "cat '<path>'"` fallback.
     */
    suspend fun readFileWithRoot(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (file.canRead()) {
                return@withContext Result.success(file.readText())
            }
            val suBin = findSuBinary() ?: "su"
            val escapedPath = file.absolutePath.replace("'", "'\\''")
            val proc = Runtime.getRuntime().exec(arrayOf(suBin, "-c", "cat '$escapedPath'"))
            val text = proc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val exit = proc.waitFor()
            if (exit == 0 || text.isNotEmpty()) {
                Result.success(text)
            } else {
                Result.failure(SecurityException("Cannot read ${file.absolutePath}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
