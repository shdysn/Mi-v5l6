package com.mi.explorer.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.data.model.FileItem
import java.io.File

object FileOpener {

    fun installApk(context: Context, file: File): Boolean {
        if (!file.exists()) {
            Toast.makeText(context, "APK file does not exist", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            val uri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot launch installer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openWithChooser(context: Context, item: FileItem) {
        val file = item.file
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Uri.fromFile(file)
            }

            val mimeType = item.mimeType
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val chooser = Intent.createChooser(intent, "Open \"${item.name}\" with")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: android.content.ActivityNotFoundException) {
            // Try generic fallback if specific mime type failed
            tryGenericFallback(context, item)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryGenericFallback(context: Context, item: FileItem) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                item.file
            )
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(fallbackIntent, "Open \"${item.name}\" with")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWithSpecificApp(context: Context, item: FileItem, packageName: String, activityName: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                item.file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                setClassName(packageName, activityName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to chooser
            openWithChooser(context, item)
        }
    }

    fun queryIntentApps(context: Context, item: FileItem): List<ResolveInfo> {
        return try {
            val uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    item.file
                )
            } catch (e: Exception) {
                Uri.fromFile(item.file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun shareFile(context: Context, item: FileItem) {
        val file = item.file
        if (!file.exists()) return
        try {
            val uri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(intent, "Share \"${item.name}\"")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMultipleFiles(context: Context, items: List<FileItem>) {
        val existingFiles = items.map { it.file }.filter { it.exists() }
        if (existingFiles.isEmpty()) return
        try {
            val uris = ArrayList<Uri>()
            for (f in existingFiles) {
                val uri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        f
                    )
                } catch (e: Exception) {
                    Uri.fromFile(f)
                }
                uris.add(uri)
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(intent, "Share ${existingFiles.size} files")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share files: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

