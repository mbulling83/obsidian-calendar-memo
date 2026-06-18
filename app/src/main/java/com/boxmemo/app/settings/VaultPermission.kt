package com.boxmemo.app.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * MANAGE_EXTERNAL_STORAGE requires a dedicated system-settings redirect on
 * API 30+ — it cannot be requested through the normal runtime permission
 * dialog. Pre-30 devices fall back to the legacy READ/WRITE_EXTERNAL_STORAGE
 * runtime permissions declared in the manifest.
 */
object VaultPermission {

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    fun buildManageAllFilesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
