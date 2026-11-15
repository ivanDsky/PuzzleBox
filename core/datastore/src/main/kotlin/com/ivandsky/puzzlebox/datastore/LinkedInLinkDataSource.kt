package com.ivandsky.puzzlebox.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists which LinkedIn profile this install is linked to, when the player accepted
 * the risk warning, and the pause latch. It takes a DataStore of its own file so backup
 * rules can exclude the link state alone. It holds nothing secret: no cookie or token
 * ever passes through it, and it reads no clock — the caller supplies the consent time.
 */
class LinkedInLinkDataSource(
    private val dataStore: DataStore<Preferences>,
) {

    val link: Flow<LinkedInLinkPrefs> = dataStore.data.map { preferences ->
        LinkedInLinkPrefs(
            profileId = preferences[KEY_PROFILE_ID],
            consentAtMs = preferences[KEY_CONSENT_AT_MS],
            pauseReason = preferences[KEY_PAUSE_REASON],
            postingConsentAtMs = preferences[KEY_POSTING_CONSENT_AT_MS],
        )
    }

    /** Links [profileId]; a fresh connect is a reconnect, so the pause latch clears in the same edit. */
    suspend fun connect(profileId: String, consentAtMs: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_PROFILE_ID] = profileId
            preferences[KEY_CONSENT_AT_MS] = consentAtMs
            preferences.remove(KEY_PAUSE_REASON)
        }
    }

    /** Latches a pause; the profile and consent stay. */
    suspend fun pause(reason: String) {
        dataStore.edit { preferences -> preferences[KEY_PAUSE_REASON] = reason }
    }

    /** Lifts the pause; the profile and consent stay. */
    suspend fun resume() {
        dataStore.edit { preferences -> preferences.remove(KEY_PAUSE_REASON) }
    }

    /**
     * Turns posting on at [consentAtMs], or off with null. It is a no-op while no profile is linked: posting consent
     * belongs to a link, so a write racing a disconnect must not outlive the wipe. A fresh [connect] does not clear it
     * (the file is wiped on disconnect, so a reconnect starts with posting off).
     */
    suspend fun setPostingConsent(consentAtMs: Long?) {
        dataStore.edit { preferences ->
            if (KEY_PROFILE_ID !in preferences) return@edit
            if (consentAtMs == null) {
                preferences.remove(KEY_POSTING_CONSENT_AT_MS)
            } else {
                preferences[KEY_POSTING_CONSENT_AT_MS] = consentAtMs
            }
        }
    }

    /** Disconnect: forgets the profile, the consents and any pause. The file holds only these keys. */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private companion object {
        val KEY_PROFILE_ID = stringPreferencesKey("linkedin_profile_id")
        val KEY_CONSENT_AT_MS = longPreferencesKey("linkedin_consent_at_ms")
        val KEY_PAUSE_REASON = stringPreferencesKey("linkedin_pause_reason")
        val KEY_POSTING_CONSENT_AT_MS = longPreferencesKey("linkedin_posting_consent_at_ms")
    }
}
