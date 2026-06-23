package com.boxmemo.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.boxmemo.app.vault.VaultSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore by preferencesDataStore(name = "vault_settings")
private val VAULT_ROOT_KEY = stringPreferencesKey("vault_root")
private val MEETINGS_HEADING_KEY = stringPreferencesKey("meetings_heading")
private val NOTES_HEADING_KEY = stringPreferencesKey("notes_heading")

/**
 * Persists the user-configured vault root path and the daily-note section
 * headings (which section holds meetings, which holds notes) across restarts.
 * Headings default to the author's own convention and are matched forgivingly
 * (see [com.boxmemo.app.vault.SectionHeading]).
 */
class VaultSettingsStore(private val context: Context) {

    val vaultRoot: Flow<String?> =
        context.vaultDataStore.data.map { it[VAULT_ROOT_KEY] }

    val meetingsHeading: Flow<String> =
        context.vaultDataStore.data.map { it[MEETINGS_HEADING_KEY] ?: VaultSettings.DEFAULT_MEETINGS_HEADING }

    val notesHeading: Flow<String> =
        context.vaultDataStore.data.map { it[NOTES_HEADING_KEY] ?: VaultSettings.DEFAULT_NOTES_HEADING }

    suspend fun setVaultRoot(path: String) {
        context.vaultDataStore.edit { it[VAULT_ROOT_KEY] = path }
    }

    suspend fun setMeetingsHeading(heading: String) {
        context.vaultDataStore.edit { it[MEETINGS_HEADING_KEY] = heading }
    }

    suspend fun setNotesHeading(heading: String) {
        context.vaultDataStore.edit { it[NOTES_HEADING_KEY] = heading }
    }
}
