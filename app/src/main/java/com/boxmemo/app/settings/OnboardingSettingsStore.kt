package com.boxmemo.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_settings")
private val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")

/**
 * Remembers whether the first-run welcome tour has been completed, so it
 * shows once for a new user (e.g. a friend installing the app) and never
 * again unless they re-open it from Settings.
 */
class OnboardingSettingsStore(private val context: Context) {
    val onboardingComplete: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[ONBOARDING_COMPLETE_KEY] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.onboardingDataStore.edit { it[ONBOARDING_COMPLETE_KEY] = complete }
    }
}
