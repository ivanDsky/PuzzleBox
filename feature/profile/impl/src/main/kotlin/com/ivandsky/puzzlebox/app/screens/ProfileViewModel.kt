package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodayLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
 * Profile's state holder (A20): the lifetime record ([ObserveGameStats], all games and each
 * registered one, plus the days played off [ObserveGameHistory]), today's identity-gated
 * board ([ObserveTodayLeaderboard]), the sign-in flow's own progress/outcome while signed
 * out, and the app's theme ([UserPreferencesRepository], N4). The activity context the
 * credential sheet needs (I2) is passed per call and never stored.
 */
class ProfileViewModel(
    private val identityService: IdentityService,
    private val idTokenProvider: GoogleIdTokenProvider,
    observeTodayLeaderboard: ObserveTodayLeaderboard,
    observeGameStats: ObserveGameStats,
    observeGameHistory: ObserveGameHistory,
    registry: GameRegistry,
    brands: GamePlayRegistry,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val signIn = MutableStateFlow(ProfileUiState.Account.SignedOut())

    private val seedRows: List<ProfileUiState.GameRow> = registry.all.map { plugin ->
        val id = plugin.definition.id
        ProfileUiState.GameRow(
            id, plugin.definition.displayName, brands.brandOf(id), solved = 0,
            best = null, rankOrder = plugin.score.rankOrder,
        )
    }

    private val account: Flow<ProfileUiState.Account> =
        combine(observeTodayLeaderboard(), signIn) { today, prompt ->
            when (today) {
                TodayLeaderboard.SignedOut -> prompt
                is TodayLeaderboard.SignedIn -> ProfileUiState.Account.SignedIn(today.identity, today.board)
            }
        }

    private val daysPlayed: Flow<Int> = observeGameHistory()
        .map { days -> days.filter { it.record != null && it.puzzleNumber != null }.map { it.day }.distinct().size }
        .distinctUntilChanged()

    private val gameRows: Flow<List<ProfileUiState.GameRow>> =
        if (seedRows.isEmpty()) {
            flowOf(emptyList())
        } else {
            val perGame = seedRows.map { row ->
                observeGameStats(row.gameId).map { row.copy(solved = it.solved, best = it.best, rankOrder = it.rankOrder) }
            }
            combine(perGame) { it.toList() }
        }

    private val initialAccount: ProfileUiState.Account = identityService.current.value
        ?.let { ProfileUiState.Account.SignedIn(it, TodayLeaderboard.Board.Loading) }
        ?: signIn.value

    val uiState: StateFlow<ProfileUiState> =
        combine(account, observeGameStats(), daysPlayed, gameRows, preferences.themeMode) { account, stats, days, games, theme ->
            ProfileUiState(account, stats, days, games, theme)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(account = initialAccount, games = seedRows),
        )

    /** Appearance (N4): persisted; the app root re-themes from the stored mode. */
    fun onSetThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

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
