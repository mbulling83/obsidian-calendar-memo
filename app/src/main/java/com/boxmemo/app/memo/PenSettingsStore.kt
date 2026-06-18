package com.boxmemo.app.memo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.penSettingsDataStore by preferencesDataStore(name = "pen_settings")
private val PEN_TYPE_KEY = stringPreferencesKey("pen_type")
private val STROKE_WIDTH_KEY = floatPreferencesKey("stroke_width")

class PenSettingsStore(private val context: Context) {

    val settings: Flow<PenSettings> = context.penSettingsDataStore.data.map { prefs ->
        PenSettings(
            penType = prefs[PEN_TYPE_KEY]?.let { name -> PenType.entries.find { it.name == name } }
                ?: PenType.FOUNTAIN,
            strokeWidth = prefs[STROKE_WIDTH_KEY] ?: 4f,
        )
    }

    suspend fun setPenType(penType: PenType) {
        context.penSettingsDataStore.edit { it[PEN_TYPE_KEY] = penType.name }
    }

    suspend fun setStrokeWidth(width: Float) {
        context.penSettingsDataStore.edit { it[STROKE_WIDTH_KEY] = width }
    }
}
