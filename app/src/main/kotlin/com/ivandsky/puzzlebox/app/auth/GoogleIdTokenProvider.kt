package com.ivandsky.puzzlebox.app.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.ivandsky.puzzlebox.app.R
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android/platform seam for Google ID-token acquisition (ticket-16 design §2): this is
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

sealed interface GoogleIdToken {
    data class Available(val token: String) : GoogleIdToken

    /**
     * No usable token. [reason] preserves the failure class so the profile UI can
     * show honest copy (ticket 24) — only [Reason.NETWORK] may blame the connection.
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

/**
 * Ticket 24: classify a Credential Manager failure before its type is lost — the
 * profile copy is chosen per class, and only NETWORK may claim a connection problem.
 * Order matters: cancellation and no-credential extend [GetCredentialException].
 */
internal fun unavailableReason(e: Exception): GoogleIdToken.Unavailable.Reason = when (e) {
    is GetCredentialCancellationException -> GoogleIdToken.Unavailable.Reason.CANCELLED
    is NoCredentialException -> GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL
    is GetCredentialException -> GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE
    is GoogleIdTokenParsingException -> GoogleIdToken.Unavailable.Reason.MALFORMED
    is IOException -> GoogleIdToken.Unavailable.Reason.NETWORK
    else -> GoogleIdToken.Unavailable.Reason.OTHER
}

/**
 * Credential Manager implementation. The web client id (`serverClientId`) is the
 * type-3 `default_web_client_id` the google-services plugin generates from the root
 * google-services.json; resolved lazily so graph construction never touches resources.
 * Every failure degrades to [GoogleIdToken.Unavailable] — the signed-out path is a
 * first-class state, never an error surface (A7).
 */
class CredentialManagerGoogleIdTokenProvider(
    private val context: Context,
) : GoogleIdTokenProvider {

    override suspend fun idToken(activityContext: Context): GoogleIdToken = try {
        val manager = CredentialManager.create(context)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val response = manager.getCredential(
            // The provider launches its own activity from this context below API 34.
            activityContext,
            GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        val credential = response.credential
        // Bug C (2026-11-02): the type check must be TYPE-first with a manual
        // `createFrom(data)` — demanding `is GoogleIdTokenCredential` throws away the
        // token on ReVanced microG (our tab): its provider returns the right type as a
        // bare CustomCredential whose bundle DOES carry the token; only the androidx
        // auto-convert to the subclass is missing there. Real GMS passes both checks.
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            if (idToken.isNotEmpty()) {
                GoogleIdToken.Available(idToken)
            } else {
                android.util.Log.d("PuzzleBoxAuth", "credential carried an empty idToken — MALFORMED")
                GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.MALFORMED)
            }
        } else {
            android.util.Log.d("PuzzleBoxAuth", "unexpected credential ${credential.type} (${credential.javaClass.simpleName}) — MALFORMED")
            GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.MALFORMED)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Bug C: the classification was invisible in the field — a Samsung freezer
        // freeze of com.android.credentialmanager produced the NETWORK copy on a
        // healthy network. DEBUG-level, tagged for one-line logcat diagnosis.
        android.util.Log.d("PuzzleBoxAuth", "idToken unavailable: ${unavailableReason(e)} (${e.javaClass.simpleName})", e)
        GoogleIdToken.Unavailable(unavailableReason(e))
    }

    suspend fun clearCredentialState() {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
