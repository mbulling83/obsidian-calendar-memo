package com.boxmemo.app.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "update_settings")
private val LAST_CHECK_KEY = longPreferencesKey("last_check_ms")
private val AUTO_CHECK_KEY = booleanPreferencesKey("auto_check_enabled")
private val AVAILABLE_TAG_KEY = stringPreferencesKey("available_tag")
private val AVAILABLE_NOTES_KEY = stringPreferencesKey("available_notes")
private val AVAILABLE_APK_URL_KEY = stringPreferencesKey("available_apk_url")

/**
 * Persists the auto-updater's bookkeeping: when we last checked GitHub (so a
 * launch check throttles to once a day), whether automatic checking is on, and
 * the release a check last found — so the "Update available" banner survives
 * process death instead of vanishing until the next throttled check.
 */
class UpdateSettingsStore(private val context: Context) {

    val autoCheckEnabled: Flow<Boolean> =
        context.updateDataStore.data.map { it[AUTO_CHECK_KEY] ?: true }

    suspend fun lastCheckMs(): Long =
        context.updateDataStore.data.map { it[LAST_CHECK_KEY] ?: 0L }.first()

    suspend fun setLastCheckMs(value: Long) {
        context.updateDataStore.edit { it[LAST_CHECK_KEY] = value }
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        context.updateDataStore.edit { it[AUTO_CHECK_KEY] = enabled }
    }

    /** The update a check last found, or null if none (or it was cleared). */
    suspend fun availableRelease(): LatestRelease? {
        val prefs = context.updateDataStore.data.first()
        val tag = prefs[AVAILABLE_TAG_KEY]?.ifBlank { null } ?: return null
        val apkUrl = prefs[AVAILABLE_APK_URL_KEY]?.ifBlank { null } ?: return null
        return LatestRelease(
            version = tag.removePrefix("v"),
            tag = tag,
            notes = prefs[AVAILABLE_NOTES_KEY].orEmpty(),
            apkUrl = apkUrl,
        )
    }

    suspend fun setAvailableRelease(release: LatestRelease?) {
        context.updateDataStore.edit {
            if (release == null) {
                it.remove(AVAILABLE_TAG_KEY)
                it.remove(AVAILABLE_NOTES_KEY)
                it.remove(AVAILABLE_APK_URL_KEY)
            } else {
                it[AVAILABLE_TAG_KEY] = release.tag
                it[AVAILABLE_NOTES_KEY] = release.notes
                it[AVAILABLE_APK_URL_KEY] = release.apkUrl
            }
        }
    }
}
