package com.boxmemo.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK to the app's cache and hands it to the system
 * package installer. The installer screen (and the one-time "install unknown
 * apps" grant) is shown by Android — a normal app can't install silently, so
 * the final confirmation is always the user's. The new APK is signed with the
 * same key, so it updates in place with no data loss.
 */
object ApkInstaller {

    /** Download [url] into cache as a clean single APK; null on any failure. */
    suspend fun download(context: Context, url: String, version: String): File? =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                // Keep only the APK we're about to install.
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "the-daily-$version.apk")
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true // browser_download_url 302s to the CDN
                    setRequestProperty("User-Agent", "the-daily-android")
                    connectTimeout = 15_000
                    readTimeout = 60_000
                }
                if (conn.responseCode !in 200..299) return@withContext null
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                if (out.length() > 0L) out else null
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    /** Launch the system installer for a downloaded [apk]. */
    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
