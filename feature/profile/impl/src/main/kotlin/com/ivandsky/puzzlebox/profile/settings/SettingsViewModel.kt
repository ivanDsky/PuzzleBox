package com.ivandsky.puzzlebox.profile.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SIGN_IN_OFFLINE = "Sign-in is unavailable right now. Check your connection."
private const val SIGN_IN_FAILED = "Sign-in failed. Please try again."

/** Progress and outcome of the Visibility section's sign-in. */
private data class SignInProgress(val running: Boolean = false, val message: String? = null)

/**
 * Settings state: the stored theme mode, the Games switches (live from [games]) and [AppBuild.versionName]; a pick is
 * saved through [preferences] and a switch through [games]. [visibility] (null: no Visibility section, repository untouched)
 * supplies and saves the player's own tier.
 */
internal class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    private val games: GameOptions,
    appBuild: AppBuild,
    private val identityService: IdentityService,
    private val idTokenProvider: GoogleIdTokenProvider,
    private val visibility: VisibilitySlot? = null,
) : ViewModel() {

    private val gamesSection: Flow<GamesSectionState> =
        combine(games.queensAutoCross, games.sudokuPeerHighlight, games.sudokuSameDigitHighlight, ::GamesSectionState)

    private val signIn = MutableStateFlow(SignInProgress())

    val uiState: StateFlow<SettingsUiState> =
        combine(
            preferences.themeMode,
            gamesSection,
            visibility?.ownTier ?: flowOf(null),
            combine(preferences.ranksFriendsDefault, preferences.vibration, ::Pair),
            preferences.showSelfDuelTimeLeft,
        ) { mode, gamesState, ownTier, (ranksFriends, vibration), timeLeft ->
            SettingsUiState(
                themeMode = mode,
                versionName = appBuild.versionName,
                games = gamesState,
                visibility = ownTier,
                ranksFriendsDefault = ranksFriends,
                vibration = vibration,
                showSelfDuelTimeLeft = timeLeft,
            )
        }.combine(signIn) { state, progress ->
            state.copy(signingIn = progress.running, signInMessage = progress.message)
        }.combine(preferences.ranksLinkedInDefault) { state, linkedIn ->
            state.copy(ranksLinkedInDefault = linkedIn)
        }.combine(identityService.current) { state, identity -> state.copy(signedIn = identity != null) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(themeMode = ThemeMode.SYSTEM, versionName = appBuild.versionName),
        )

    /** Appearance: persisted; the app root re-themes from the stored mode. */
    fun onSetThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /** Visibility: written through the slot; the section follows the stored tier back. */
    fun onSelectVisibility(tier: VisibilityTier) {
        val slot = visibility ?: return
        viewModelScope.launch { slot.onSelect(tier) }
    }

    /**
     * Visibility while signed out: the same Google sign-in as Profile. Success shows as the section following the tier;
     * a failure is said in [SettingsUiState.signInMessage], a backed-out sheet is not a failure.
     */
    fun onSignIn(activityContext: Context) {
        if (signIn.value.running) return
        signIn.value = SignInProgress(running = true)
        viewModelScope.launch {
            val message = when (val token = idTokenProvider.idToken(activityContext)) {
                is GoogleIdToken.Available -> when (val result = identityService.signInWithGoogleIdToken(token.token)) {
                    is AuthResult.Ok -> null
                    is AuthResult.Err -> if (result.error is AuthError.Unavailable) SIGN_IN_OFFLINE else SIGN_IN_FAILED
                }
                is GoogleIdToken.Unavailable -> when (token.reason) {
                    GoogleIdToken.Unavailable.Reason.CANCELLED -> null
                    GoogleIdToken.Unavailable.Reason.NETWORK -> SIGN_IN_OFFLINE
                    else -> SIGN_IN_FAILED
                }
            }
            signIn.value = SignInProgress(message = message)
        }
    }

    /** Ranks: the scope every hub landing opens on. Only with the [visibility] slot, which is what the flag builds. */
    fun onSetRanksFriendsDefault(friends: Boolean) {
        if (visibility == null) return
        viewModelScope.launch { preferences.setRanksFriendsDefault(friends) }
    }

    /** Ranks: whether a hub landing counts LinkedIn games only. */
    fun onSetRanksLinkedInDefault(linkedIn: Boolean) {
        viewModelScope.launch { preferences.setRanksLinkedInDefault(linkedIn) }
    }

    /** Vibration: the app-wide haptics switch; the root gate follows the stored value at once. */
    fun onSetVibration(on: Boolean) {
        viewModelScope.launch { preferences.setVibration(on) }
    }

    /** Play: the stored time-left switch the self-duel timer reads. */
    fun onSetShowSelfDuelTimeLeft(on: Boolean) {
        viewModelScope.launch { preferences.setShowSelfDuelTimeLeft(on) }
    }

    /** Games: written to the store the in-game overflow toggle reads, so the board shows it next time it opens. */
    fun onSetQueensAutoCross(enabled: Boolean) {
        viewModelScope.launch { games.setQueensAutoCross(enabled) }
    }

    fun onSetSudokuPeerHighlight(enabled: Boolean) {
        viewModelScope.launch { games.setSudokuPeerHighlight(enabled) }
    }

    fun onSetSudokuSameDigitHighlight(enabled: Boolean) {
        viewModelScope.launch { games.setSudokuSameDigitHighlight(enabled) }
    }
}
