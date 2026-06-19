package com.boxmemo.app.vaultnotes

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentItemsDataStore by preferencesDataStore(name = "vault_notes_recent")
private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
private val RECENT_FILES_KEY = stringPreferencesKey("recent_files")

/**
 * Persists the most recent filename searches and opened files for the Vault
 * Notes file picker, so the search page can offer quick re-entry points the
 * moment it's opened. Each list is stored as newline-joined strings (queries
 * are single-line and file paths never contain newlines), most-recent first,
 * de-duplicated, and capped at [MAX_ITEMS].
 */
class RecentItemsStore(private val context: Context) {

    val recentSearches: Flow<List<String>> =
        context.recentItemsDataStore.data.map { it[RECENT_SEARCHES_KEY].decode() }

    val recentFiles: Flow<List<String>> =
        context.recentItemsDataStore.data.map { it[RECENT_FILES_KEY].decode() }

    suspend fun addSearch(query: String) {
        val value = query.trim()
        if (value.isEmpty()) return
        context.recentItemsDataStore.edit { prefs ->
            prefs[RECENT_SEARCHES_KEY] = prefs[RECENT_SEARCHES_KEY].decode().promote(value).encode()
        }
    }

    suspend fun addFile(path: String) {
        if (path.isEmpty()) return
        context.recentItemsDataStore.edit { prefs ->
            prefs[RECENT_FILES_KEY] = prefs[RECENT_FILES_KEY].decode().promote(path).encode()
        }
    }

    private fun List<String>.promote(value: String): List<String> =
        (listOf(value) + filterNot { it == value }).take(MAX_ITEMS)

    private fun String?.decode(): List<String> =
        this?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()

    private fun List<String>.encode(): String = joinToString("\n")

    companion object {
        const val MAX_ITEMS = 8
    }
}
