package com.ivandsky.puzzlebox.auth

import android.content.Context

/**
 * Android/platform seam for Google ID-token acquisition: this is
 * the Credential-Manager-specific half of Google sign-in; the pure
 * [com.ivandsky.puzzlebox.data.identity.IdentityService] only ever sees the token string,
 * which is exactly what makes the backend swappable.
 */
interface GoogleIdTokenProvider {
    /**
     * [activityContext] must be an Activity context below API 34: the Credential
     * Manager provider launches its own activity from it, which crashes from an
     * application context. The application context stays internal (resources,
     * clearCredentialState).
     */
    suspend fun idToken(activityContext: Context): GoogleIdToken
}
