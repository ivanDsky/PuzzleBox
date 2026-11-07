package com.ivandsky.puzzlebox.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleIdToken {
    data class Available(val token: String) : GoogleIdToken

    /**
     * No usable token. [reason] preserves the failure class so the profile UI can
     * show honest copy — only [Reason.NETWORK] may blame the connection.
     */
    data class Unavailable(val reason: Reason) : GoogleIdToken {
        enum class Reason {
            /** No Google account/credential on this device ([NoCredentialException]). */
            NO_CREDENTIAL,

            /** Credential Manager or its provider down/unsupported ([GetCredentialException], e.g. API availability). */
            PROVIDER_UNAVAILABLE,

            /** User closed the sign-in sheet ([GetCredentialCancellationException]) — a choice, not an error. */
            CANCELLED,

            /** Network/IO failure reaching the credential provider. */
            NETWORK,

            /** Credential arrived but was unusable ([GoogleIdTokenParsingException], empty token). */
            MALFORMED,

            /** Anything else. */
            OTHER,
        }
    }
}
