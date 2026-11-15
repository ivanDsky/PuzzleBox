package com.ivandsky.puzzlebox.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists which LinkedIn profile this install is linked to, when the player accepted
 * the risk warning, the pause latch and the linked profile's post log. It takes a DataStore
 * of its own file so backup rules can exclude the link state alone. It holds nothing secret:
 * no cookie or token ever passes through it, and it reads no clock — the caller supplies the
 * consent time.
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
            postedRefs = preferences[KEY_POSTED_REFS].orEmpty(),
            sendingRefs = preferences[KEY_SENDING_REFS].orEmpty(),
        )
    }

    /**
     * Links [profileId]; a fresh connect is a reconnect, so the pause latch clears in the same edit. The post
     * log belongs to the profile that wrote it: it stays for the same profile and goes when another one links.
     */
    suspend fun connect(profileId: String, consentAtMs: Long) {
        dataStore.edit { preferences ->
            if (preferences[KEY_PROFILE_ID] != profileId) {
                preferences.remove(KEY_POSTED_REFS)
                preferences.remove(KEY_SENDING_REFS)
            }
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

    /**
     * Marks [ref] as being sent. False, storing nothing, unless [profileId] is the linked profile: the check and
     * the write are one edit, so a marker never lands in a link that was wiped or replaced meanwhile.
     */
    suspend fun markSending(profileId: String, ref: String): Boolean {
        val stored = dataStore.edit { preferences ->
            if (preferences[KEY_PROFILE_ID] != profileId) return@edit
            preferences[KEY_SENDING_REFS] = preferences[KEY_SENDING_REFS].orEmpty() + ref
        }
        return stored[KEY_PROFILE_ID] == profileId
    }

    /**
     * Ends the send of [ref]: its marker goes, and with [posted] the ref is remembered as posted. A no-op unless
     * [profileId] is the linked profile, for the same reason as [markSending].
     */
    suspend fun settleSend(profileId: String, ref: String, posted: Boolean) {
        dataStore.edit { preferences ->
            if (preferences[KEY_PROFILE_ID] != profileId) return@edit
            preferences[KEY_SENDING_REFS] = preferences[KEY_SENDING_REFS].orEmpty() - ref
            if (posted) preferences[KEY_POSTED_REFS] = preferences[KEY_POSTED_REFS].orEmpty() + ref
        }
    }

    /** Disconnect: forgets the profile, the consents, any pause and the post log. The file holds only these keys. */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private companion object {
        val KEY_PROFILE_ID = stringPreferencesKey("linkedin_profile_id")
        val KEY_CONSENT_AT_MS = longPreferencesKey("linkedin_consent_at_ms")
        val KEY_PAUSE_REASON = stringPreferencesKey("linkedin_pause_reason")
        val KEY_POSTING_CONSENT_AT_MS = longPreferencesKey("linkedin_posting_consent_at_ms")
        val KEY_POSTED_REFS = stringSetPreferencesKey("linkedin_posted_refs")
        val KEY_SENDING_REFS = stringSetPreferencesKey("linkedin_sending_refs")
    }
}
