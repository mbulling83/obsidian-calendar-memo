package com.boxmemo.app.hwr

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recognitionPrefsDataStore by preferencesDataStore(name = "recognition_prefs")
private val LAST_METHOD_KEY = stringPreferencesKey("last_recognition_method")

/**
 * Remembers the last-used recognition method (R9) as the default for the
 * next capture, with an easy per-capture override — never forces a choice
 * the user already made once today.
 */
class RecognitionMethodPreference(private val context: Context) {

    val lastUsedMethod: Flow<RecognitionMethod> =
        context.recognitionPrefsDataStore.data.map { prefs ->
            when (prefs[LAST_METHOD_KEY]) {
                RecognitionMethod.AI_VISION.name -> RecognitionMethod.AI_VISION
                else -> RecognitionMethod.ONYX_BUILT_IN
            }
        }

    suspend fun setLastUsedMethod(method: RecognitionMethod) {
        context.recognitionPrefsDataStore.edit { it[LAST_METHOD_KEY] = method.name }
    }
}
