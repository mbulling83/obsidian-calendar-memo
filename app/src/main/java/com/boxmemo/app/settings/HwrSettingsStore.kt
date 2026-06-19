package com.boxmemo.app.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.boxmemo.app.hwr.HwrEngineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hwrSettingsDataStore by preferencesDataStore(name = "hwr_settings")
private val HWR_ENGINE_KEY = stringPreferencesKey("hwr_engine")

/** Persists which handwriting recognizer the Convert action uses. */
class HwrSettingsStore(private val context: Context) {

    val engine: Flow<HwrEngineType> = context.hwrSettingsDataStore.data.map { prefs ->
        prefs[HWR_ENGINE_KEY]?.let { name -> HwrEngineType.entries.find { it.name == name } }
            ?: HwrEngineType.ONYX
    }

    suspend fun setEngine(engine: HwrEngineType) {
        context.hwrSettingsDataStore.edit { it[HWR_ENGINE_KEY] = engine.name }
    }
}
