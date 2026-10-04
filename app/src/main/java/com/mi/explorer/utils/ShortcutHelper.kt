package com.mi.explorer.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mi.explorer.MainActivity
import com.mi.explorer.R
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.widget.MiStorageWidgetProvider

object ShortcutHelper {

    const val EXTRA_SHORTCUT_PATH = "EXTRA_SHORTCUT_PATH"

    fun pinFileOrFolderToHomeScreen(context: Context, item: FileItem): Boolean {
        return try {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                return false
            }
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_PATH, item.path)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val shortcutId = "mi_shortcut_${item.path.hashCode()}"
            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(item.name.take(18))
                .setLongLabel(if (item.isDirectory) "Folder: ${item.name}" else "File: ${item.name}")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(launchIntent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        } catch (_: Exception) {
            false
        }
    }

    fun requestPinStorageWidget(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, MiStorageWidgetProvider::class.java)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    return appWidgetManager.requestPinAppWidget(provider, null, null)
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
