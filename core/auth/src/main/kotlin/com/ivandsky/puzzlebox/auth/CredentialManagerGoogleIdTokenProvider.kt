package com.ivandsky.puzzlebox.auth

import android.content.Context
import androidx.annotation.StringRes
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
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Classify a Credential Manager failure before its type is lost — the profile copy
 * is chosen per class, and only NETWORK may claim a connection problem.
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
 * google-services.json into the app's resources — the app hands its id over as
 * [serverClientIdRes]; resolved lazily so graph construction never touches resources.
 * Every failure degrades to [GoogleIdToken.Unavailable] — the signed-out path is a
 * first-class state, never an error surface (A7).
 */
internal class CredentialManagerGoogleIdTokenProvider(
    private val context: Context,
    @StringRes private val serverClientIdRes: Int,
) : GoogleIdTokenProvider {

    override suspend fun idToken(activityContext: Context): GoogleIdToken = try {
        val manager = CredentialManager.create(context)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(serverClientIdRes))
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
