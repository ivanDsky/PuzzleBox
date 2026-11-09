package com.ivandsky.puzzlebox.compete.create

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.IdentityService

/**
 * One Google sign-in try for a duel screen: the account picker through [idTokens], then the token to [identity].
 * The result is null when the account is signed in, otherwise how the screen should read: a dismissed picker is
 * [DuelSignIn.Idle], a lost connection [DuelSignIn.Offline], anything else [DuelSignIn.Failed].
 */
internal class DuelSignInStep(
    private val identity: IdentityService,
    private val idTokens: GoogleIdTokenProvider,
) {
    suspend operator fun invoke(activityContext: Context): DuelSignIn? =
        when (val token = idTokens.idToken(activityContext)) {
            is GoogleIdToken.Available -> when (val result = identity.signInWithGoogleIdToken(token.token)) {
                is AuthResult.Ok -> null
                is AuthResult.Err -> if (result.error is AuthError.Unavailable) DuelSignIn.Offline else DuelSignIn.Failed
            }
            is GoogleIdToken.Unavailable -> when (token.reason) {
                GoogleIdToken.Unavailable.Reason.CANCELLED -> DuelSignIn.Idle
                GoogleIdToken.Unavailable.Reason.NETWORK -> DuelSignIn.Offline
                else -> DuelSignIn.Failed
            }
        }
}
