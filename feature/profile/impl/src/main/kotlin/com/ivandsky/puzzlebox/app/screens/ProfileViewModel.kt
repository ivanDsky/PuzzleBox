package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.ObserveTodayLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Ticket 24 — honest sign-in copy per failure class. "Check your connection" is
// reserved for the network branches; every other class says what actually failed.
private const val SIGN_IN_NETWORK_MESSAGE = "Sign-in is unavailable right now. Check your connection."
private const val SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE =
    "Google sign-in isn't available right now. Try again, or try another account."
private const val SIGN_IN_RETRY_MESSAGE = "Sign-in failed. Please try again."

/**
 * Profile's state holder: today's identity-gated board ([ObserveTodayLeaderboard]) plus
 * the sign-in flow's own progress/outcome, shown while signed out. The board is the
 * featured game's ([GetFeaturedGame]), so its display name rides along for the copy.
 * The activity context the credential sheet needs (I2) is passed per call and never stored.
 */
class ProfileViewModel(
    private val identityService: IdentityService,
    private val idTokenProvider: GoogleIdTokenProvider,
    observeTodayLeaderboard: ObserveTodayLeaderboard,
    featuredGame: GetFeaturedGame,
) : ViewModel() {

    private val signIn = MutableStateFlow(ProfileUiState.SignedOut())

    private val gameName: String = featuredGame().displayName

    val uiState: StateFlow<ProfileUiState> =
        combine(observeTodayLeaderboard(), signIn) { today, prompt ->
            when (today) {
                TodayLeaderboard.SignedOut -> prompt
                is TodayLeaderboard.SignedIn -> ProfileUiState.SignedIn(today.identity, today.board, gameName)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = identityService.current.value
                ?.let { ProfileUiState.SignedIn(it, TodayLeaderboard.Board.Loading, gameName) }
                ?: signIn.value,
        )

    fun onSignIn(activityContext: Context) {
        viewModelScope.launch {
            signIn.update { it.copy(signingIn = true, signInMessage = null) }
            // Ticket 24: copy must match the failure class. A credential-provider
            // failure never reached the backend, so it is not an AuthError.Unavailable
            // (that class is the backend network path) — it maps straight to copy.
            val message: String? = when (val token = idTokenProvider.idToken(activityContext)) {
                is GoogleIdToken.Available ->
                    when (val result = identityService.signInWithGoogleIdToken(token.token)) {
                        is AuthResult.Ok -> null
                        is AuthResult.Err -> friendly(result.error)
                    }
                is GoogleIdToken.Unavailable -> friendly(token.reason)
            }
            signIn.update { it.copy(signingIn = false, signInMessage = message) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch { identityService.signOut() }
    }

    private fun friendly(error: AuthError): String = when (error) {
        // Backend half: FirebaseNetworkException — the only branch allowed to blame
        // the connection (ticket 24).
        is AuthError.Unavailable -> SIGN_IN_NETWORK_MESSAGE
        // Token-exchange failure (invalid/expired token, account blocked): the honest
        // remediation is retry or another account, not "check your connection".
        is AuthError.Rejected -> SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE
        is AuthError.Malformed -> "The account response was unusable. Please try again."
        is AuthError.Unknown -> "Sign-in failed unexpectedly. Please try again."
    }

    /** Credential Manager half (ticket 24): copy chosen per preserved failure class. */
    private fun friendly(reason: GoogleIdToken.Unavailable.Reason): String = when (reason) {
        GoogleIdToken.Unavailable.Reason.NETWORK -> SIGN_IN_NETWORK_MESSAGE
        GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL,
        GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE,
        -> SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE
        GoogleIdToken.Unavailable.Reason.CANCELLED,
        GoogleIdToken.Unavailable.Reason.MALFORMED,
        GoogleIdToken.Unavailable.Reason.OTHER,
        -> SIGN_IN_RETRY_MESSAGE
    }
}
