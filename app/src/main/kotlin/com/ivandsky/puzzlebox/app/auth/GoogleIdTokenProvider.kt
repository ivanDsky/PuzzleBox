package com.ivandsky.puzzlebox.app.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.ivandsky.puzzlebox.app.R
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android/platform seam for Google ID-token acquisition (ticket-16 design §2): this is
 * the Credential-Manager-specific half of Google sign-in; the pure
 * [com.ivandsky.puzzlebox.data.identity.IdentityService] only ever sees the token string,
 * which is exactly what makes the backend swappable.
 */
interface GoogleIdTokenProvider {
    suspend fun idToken(): GoogleIdToken
}

sealed interface GoogleIdToken {
    data class Available(val token: String) : GoogleIdToken

    /** No usable Google credential on this device, user cancelled, or provider down (A7). */
    data object Unavailable : GoogleIdToken
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

    override suspend fun idToken(): GoogleIdToken = try {
        val manager = CredentialManager.create(context)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val response = manager.getCredential(
            context,
            GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        val credential = response.credential
        if (credential is GoogleIdTokenCredential && credential.idToken.isNotEmpty()) {
            GoogleIdToken.Available(credential.idToken)
        } else {
            GoogleIdToken.Unavailable
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: GetCredentialCancellationException) {
        GoogleIdToken.Unavailable // user closed the sheet — a choice, not an error
    } catch (_: NoCredentialException) {
        GoogleIdToken.Unavailable
    } catch (_: GetCredentialException) {
        GoogleIdToken.Unavailable
    } catch (_: GoogleIdTokenParsingException) {
        GoogleIdToken.Unavailable
    }

    suspend fun clearCredentialState() {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
