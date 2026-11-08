package com.ivandsky.puzzlebox.play.board

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

/** One screen's Google sign-in for a board: status plus the action. Plain class; its owner constructs it. */
internal class BoardSignInHolder(
    private val identity: IdentityService,
    private val idTokens: GoogleIdTokenProvider,
) {

    private val state = MutableStateFlow(BoardSignIn.Idle)

    /** How this screen's last sign-in went. */
    val status: StateFlow<BoardSignIn> = state.asStateFlow()

    /** The board's Google button: [activityContext] hosts Google's account picker, and is not kept. A try under way is not repeated. */
    suspend fun signIn(activityContext: Context) {
        if (state.getAndUpdate { BoardSignIn.SigningIn } == BoardSignIn.SigningIn) return
        state.value = when (val token = idTokens.idToken(activityContext)) {
            is GoogleIdToken.Available -> when (val result = identity.signInWithGoogleIdToken(token.token)) {
                is AuthResult.Ok -> BoardSignIn.Idle
                is AuthResult.Err -> if (result.error is AuthError.Unavailable) BoardSignIn.Offline else BoardSignIn.Failed
            }
            is GoogleIdToken.Unavailable -> when (token.reason) {
                GoogleIdToken.Unavailable.Reason.CANCELLED -> BoardSignIn.Idle
                GoogleIdToken.Unavailable.Reason.NETWORK -> BoardSignIn.Offline
                else -> BoardSignIn.Failed
            }
        }
    }
}
