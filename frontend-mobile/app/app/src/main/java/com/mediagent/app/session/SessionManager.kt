package com.mediagent.app.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chetana_session")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val loggedInKey = booleanPreferencesKey("is_logged_in")
    private val emailKey = stringPreferencesKey("email")
    private val familyIdKey = stringPreferencesKey("family_id")
    private val languageKey = stringPreferencesKey("language")
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")
    private val voiceEnabledKey = booleanPreferencesKey("voice_enabled")

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[loggedInKey] ?: false }
    val familyId: Flow<String> = context.dataStore.data.map { it[familyIdKey] ?: "cf7eb826-c613-4850-9cc0-b960ebfd7f5b" }
    val email: Flow<String> = context.dataStore.data.map { it[emailKey] ?: "" }
    val language: Flow<String> = context.dataStore.data.map { it[languageKey] ?: "en" }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[onboardingCompleteKey] ?: false }
    val voiceEnabled: Flow<Boolean> = context.dataStore.data.map { it[voiceEnabledKey] ?: false }

    suspend fun saveSession(email: String, familyId: String) {
        context.dataStore.edit {
            it[loggedInKey] = true
            it[emailKey] = email
            it[familyIdKey] = familyId
        }
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[languageKey] = code }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[onboardingCompleteKey] = true }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[voiceEnabledKey] = enabled }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it[loggedInKey] = false
            it[emailKey] = ""
            it[familyIdKey] = "cf7eb826-c613-4850-9cc0-b960ebfd7f5b"
        }
    }
}
