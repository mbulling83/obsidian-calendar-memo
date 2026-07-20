package com.boxmemo.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads a release APK to the app's cache and hands it to the system
 * package installer. The installer screen (and the one-time "install unknown
 * apps" grant) is shown by Android — a normal app can't install silently, so
 * the final confirmation is always the user's. The new APK is signed with the
 * same key, so it updates in place with no data loss.
 *
 * Before the installer is launched, the downloaded file is verified: the byte
 * count must match the response's Content-Length, and the APK must carry our
 * own package name and the same signing certificate as the running app — a
 * truncated, substituted, or foreign APK is deleted instead of offered.
 */
object ApkInstaller {

    /** Download [url] into cache as a clean, verified single APK; null on any failure. */
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
                val expectedLength = conn.contentLengthLong
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                val complete = out.length() > 0L &&
                    (expectedLength <= 0L || out.length() == expectedLength)
                if (complete && verifyApk(context, out)) {
                    out
                } else {
                    out.delete()
                    null
                }
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    /**
     * True iff [apk] is an update for *this* app: same package name and the
     * same signing certificate (SHA-256 of the cert bytes) as the running app.
     */
    private fun verifyApk(context: Context, apk: File): Boolean = try {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archiveDigests = signingCertDigests(archive?.signingInfo)
        val installedDigests = signingCertDigests(installed.signingInfo)
        archive != null &&
            archive.packageName == context.packageName &&
            archiveDigests.isNotEmpty() &&
            installedDigests.isNotEmpty() &&
            archiveDigests.any { it in installedDigests }
    } catch (_: Exception) {
        false
    }

    /** SHA-256 digests of the signing certificates in [info]. */
    private fun signingCertDigests(info: SigningInfo?): Set<String> {
        val signatures = when {
            info == null -> return emptySet()
            info.hasMultipleSigners() -> info.apkContentsSigners
            else -> info.signingCertificateHistory
        } ?: return emptySet()
        val sha256 = MessageDigest.getInstance("SHA-256")
        return signatures.mapTo(mutableSetOf()) { signature ->
            sha256.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
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
