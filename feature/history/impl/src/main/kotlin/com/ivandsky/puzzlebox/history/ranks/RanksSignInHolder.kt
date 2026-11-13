package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.IdentityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * The hub's Google sign-in: [status] plus the action. Success needs no state of its own: the
 * identity flips and every board reads again. Play's board holds the same flow; ADR-0001 keeps
 * features from reaching into each other's impl, so the hub has its own.
 */
internal class RanksSignInHolder(
    private val identity: IdentityService,
    private val idTokens: GoogleIdTokenProvider,
) {

    private val state = MutableStateFlow(RanksSignIn.Idle)

    val status: StateFlow<RanksSignIn> = state.asStateFlow()

    /** [activityContext] hosts Google's account picker and is not kept. A try under way is not repeated. */
    suspend fun signIn(activityContext: Context) {
        if (state.getAndUpdate { RanksSignIn.SigningIn } == RanksSignIn.SigningIn) return
        state.value = when (val token = idTokens.idToken(activityContext)) {
            is GoogleIdToken.Available -> when (val result = identity.signInWithGoogleIdToken(token.token)) {
                is AuthResult.Ok -> RanksSignIn.Idle
                is AuthResult.Err -> if (result.error is AuthError.Unavailable) RanksSignIn.Offline else RanksSignIn.Failed
            }
            is GoogleIdToken.Unavailable -> when (token.reason) {
                GoogleIdToken.Unavailable.Reason.CANCELLED -> RanksSignIn.Idle
                GoogleIdToken.Unavailable.Reason.NETWORK -> RanksSignIn.Offline
                else -> RanksSignIn.Failed
            }
        }
    }
}
