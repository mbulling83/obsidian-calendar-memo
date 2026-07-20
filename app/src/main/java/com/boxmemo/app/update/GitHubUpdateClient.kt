package com.boxmemo.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The latest published release that carries an installable APK asset.
 * [version] is the tag with any leading "v" stripped, for comparison.
 */
data class LatestRelease(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
)

/**
 * Reads the newest GitHub release for the app's own repo. This is the app's
 * *only* network call — deliberately minimal (plain HttpURLConnection, short
 * timeouts) and fail-silent: any error (offline, rate-limited, malformed)
 * returns null so a launch-time check never blocks or crashes the UI.
 *
 * The repo is public, so no token is needed and nothing secret is embedded.
 */
class GitHubUpdateClient(
    private val repo: String = "mbulling83/obsidian-calendar-memo",
) {
    suspend fun fetchLatest(): LatestRelease? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "the-daily-android")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { return@withContext null }
            val notes = json.optString("body")
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                        .ifBlank { null }
                        ?.takeIf { isTrustedApkUrl(it) }
                    if (apkUrl != null) break
                }
            }
            val downloadUrl = apkUrl ?: return@withContext null
            LatestRelease(
                version = tag.removePrefix("v"),
                tag = tag,
                notes = notes,
                apkUrl = downloadUrl,
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        /**
         * Only accept APK download URLs that are https and hosted by GitHub —
         * a compromised or spoofed API response must not be able to point the
         * installer at an arbitrary server.
         */
        internal fun isTrustedApkUrl(raw: String): Boolean {
            val url = runCatching { URL(raw) }.getOrNull() ?: return false
            if (!url.protocol.equals("https", ignoreCase = true)) return false
            val host = url.host.orEmpty().lowercase()
            return host == "github.com" ||
                host == "objects.githubusercontent.com" ||
                host.endsWith(".githubusercontent.com")
        }
    }
}
