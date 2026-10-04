package com.mi.explorer.utils

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
        if (suBinary == null) {
            // Check fallback via PATH
            var pathSu: String? = null
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

        val effectiveSu = suBinary ?: "su"
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

    fun getFileLinuxPermissions(file: File): String {
        return buildString {
            append(if (file.isDirectory) "d" else "-")
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
            append(if (file.canExecute()) "x" else "-")
            append("r-x") // system group default display
            append("r-x") // others
        }
    }
}
