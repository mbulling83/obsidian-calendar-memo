package com.boxmemo.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore by preferencesDataStore(name = "vault_settings")
private val VAULT_ROOT_KEY = stringPreferencesKey("vault_root")

/** Persists the user-configured vault root path across app restarts. */
class VaultSettingsStore(private val context: Context) {

    val vaultRoot: Flow<String?> =
        context.vaultDataStore.data.map { it[VAULT_ROOT_KEY] }

    suspend fun setVaultRoot(path: String) {
        context.vaultDataStore.edit { it[VAULT_ROOT_KEY] = path }
    }
}
