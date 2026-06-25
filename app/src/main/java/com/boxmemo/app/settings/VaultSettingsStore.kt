package com.boxmemo.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.boxmemo.app.vault.VaultSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore by preferencesDataStore(name = "vault_settings")
private val VAULT_ROOT_KEY = stringPreferencesKey("vault_root")
private val DAILY_NOTE_TEMPLATE_KEY = stringPreferencesKey("daily_note_template")
private val DAILY_NOTE_TEMPLATE_PATH_KEY = stringPreferencesKey("daily_note_template_path")
private val AUTO_CREATE_MISSING_NOTES_KEY = booleanPreferencesKey("auto_create_missing_notes")
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

    val dailyNoteTemplate: Flow<String> =
        context.vaultDataStore.data.map { it[DAILY_NOTE_TEMPLATE_KEY] ?: VaultSettings.DEFAULT_TEMPLATE }

    /**
     * Path to the Templater template used to fill a newly-created daily note,
     * relative to the vault root (or absolute). Null until the user picks one —
     * note creation then falls back to a minimal heading scaffold.
     */
    val dailyNoteTemplatePath: Flow<String?> =
        context.vaultDataStore.data.map { it[DAILY_NOTE_TEMPLATE_PATH_KEY] }

    /**
     * Whether the app should auto-create a missing daily note when the user
     * adds to it (quick-add / handwriting conversion). Off by default: the
     * native template render is best-effort (dynamic Templater tags are
     * stripped), so generating pages is opt-in. The manual "Create note" button
     * works regardless.
     */
    val autoCreateMissingNotes: Flow<Boolean> =
        context.vaultDataStore.data.map { it[AUTO_CREATE_MISSING_NOTES_KEY] ?: false }

    val meetingsHeading: Flow<String> =
        context.vaultDataStore.data.map { it[MEETINGS_HEADING_KEY] ?: VaultSettings.DEFAULT_MEETINGS_HEADING }

    val notesHeading: Flow<String> =
        context.vaultDataStore.data.map { it[NOTES_HEADING_KEY] ?: VaultSettings.DEFAULT_NOTES_HEADING }

    suspend fun setVaultRoot(path: String) {
        context.vaultDataStore.edit { it[VAULT_ROOT_KEY] = path }
    }

    suspend fun setDailyNoteTemplate(template: String) {
        context.vaultDataStore.edit { it[DAILY_NOTE_TEMPLATE_KEY] = template }
    }

    /** Sets the daily-note template path, or clears it when [path] is blank. */
    suspend fun setDailyNoteTemplatePath(path: String) {
        context.vaultDataStore.edit {
            val trimmed = path.trim()
            if (trimmed.isEmpty()) it.remove(DAILY_NOTE_TEMPLATE_PATH_KEY) else it[DAILY_NOTE_TEMPLATE_PATH_KEY] = trimmed
        }
    }

    suspend fun setAutoCreateMissingNotes(enabled: Boolean) {
        context.vaultDataStore.edit { it[AUTO_CREATE_MISSING_NOTES_KEY] = enabled }
    }

    suspend fun setMeetingsHeading(heading: String) {
        context.vaultDataStore.edit { it[MEETINGS_HEADING_KEY] = heading }
    }

    suspend fun setNotesHeading(heading: String) {
        context.vaultDataStore.edit { it[NOTES_HEADING_KEY] = heading }
    }
}
