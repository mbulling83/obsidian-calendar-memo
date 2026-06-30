package com.boxmemo.app.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "update_settings")
private val LAST_CHECK_KEY = longPreferencesKey("last_check_ms")
private val AUTO_CHECK_KEY = booleanPreferencesKey("auto_check_enabled")

/**
 * Persists the auto-updater's bookkeeping: when we last checked GitHub (so a
 * launch check throttles to once a day) and whether automatic checking is on.
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
}
