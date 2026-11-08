package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLatestSolvedToday
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.toDailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.profile.board.GameRow
import com.ivandsky.puzzlebox.profile.board.LatestSolved
import com.ivandsky.puzzlebox.profile.board.PerGameBoard
import com.ivandsky.puzzlebox.profile.board.selectedBoardGame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Ticket 24 — honest sign-in copy per failure class. "Check your connection" is
// reserved for the network branches; every other class says what actually failed.
private const val SIGN_IN_NETWORK_MESSAGE = "Sign-in is unavailable right now. Check your connection."
private const val SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE =
    "Google sign-in isn't available right now. Try again, or try another account."
private const val SIGN_IN_RETRY_MESSAGE = "Sign-in failed. Please try again."

/** [SavedStateHandle] key of the explicit board pick (a game id); absent = follow the default. */
private const val SELECTED_GAME_KEY = "selectedGame"

/**
 * Profile's state holder (A20): the lifetime record ([ObserveGameStats], all games and each
 * registered one, plus the days played off [ObserveGameHistory]), today's identity-gated
 * board ([ObserveLeaderboard.today] of the featured game), the sign-in flow's own progress/outcome while signed
 * The activity context the
 * credential sheet needs (I2) is passed per call and never stored.
 *
 * The board is the selected
 * game's: an explicit [onSelectGame] pick, else [ObserveLatestSolvedToday], else the first game
 * (see `selectedBoardGame`); the board stays Loading until the first completions emission. The
 * pick lives in [savedStateHandle] under `selectedGame` (null = follow the default), so it
 * survives rotation and process death but not an app relaunch. This is the repo's first
 * `SavedStateHandle`: Koin injects it in `ProfileModule`, tests pass `SavedStateHandle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val identityService: IdentityService,
    private val idTokenProvider: GoogleIdTokenProvider,
    observeLeaderboard: ObserveLeaderboard,
    getFeaturedGame: GetFeaturedGame,
    observeGameStats: ObserveGameStats,
    observeGameHistory: ObserveGameHistory,
    registry: GameRegistry,
    brands: GamePlayRegistry,
    private val preferences: UserPreferencesRepository,
    observeLatestSolvedToday: ObserveLatestSolvedToday,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val signIn = MutableStateFlow(ProfileUiState.Account.SignedOut())

    private val seedRows: List<GameRow> = registry.all.map { plugin ->
        val id = plugin.definition.id
        GameRow(
            id, plugin.definition.displayName, brands.brandOf(id), solved = 0,
            best = null, rankOrder = plugin.score.rankOrder,
        )
    }

    private val gameIds: List<GameId> = registry.all.map { it.definition.id }
    private val featuredId: GameId = getFeaturedGame().id
    private val rankOrderOf: (GameId) -> RankOrder = registry::rankOrderOf

    private val pick: Flow<GameId?> =
        savedStateHandle.getStateFlow<String?>(SELECTED_GAME_KEY, null).map { it?.let(::GameId) }

    private val latestSolved: Flow<LatestSolved> =
        observeLatestSolvedToday().map<GameId?, LatestSolved> { LatestSolved.Known(it) }.onStart { emit(LatestSolved.Pending) }

    /** The board's game (null while its default is pending). One emission per change. */
    private val selection: Flow<GameId?> =
        combine(pick, latestSolved) { picked, latest -> selectedBoardGame(picked, latest, gameIds) }.distinctUntilChanged()

    /** The observed board paired with its game, so a switch never shows the previous game's rows under the new chip. */
    private data class Feed(val selected: GameId?, val today: TodayLeaderboard)

    private val feed: Flow<Feed> = selection.flatMapLatest { selected ->
        observeLeaderboard.today(selected ?: featuredId).map { Feed(selected, it) }
    }

    private fun Feed.toAccount(prompt: ProfileUiState.Account.SignedOut): ProfileUiState.Account = when (today) {
        TodayLeaderboard.SignedOut -> prompt
        is TodayLeaderboard.SignedIn -> ProfileUiState.Account.SignedIn(today.identity)
    }

    private fun Feed.toBoard(): PerGameBoard {
        val state: DailyBoardState =
            if (selected == null && today is TodayLeaderboard.SignedIn) DailyBoardState.Loading else today.toDailyBoardState()
        return PerGameBoard(selected, rankOrderOf(selected ?: featuredId), state)
    }

    private data class Session(val account: ProfileUiState.Account, val board: PerGameBoard)

    private val session: Flow<Session> =
        combine(feed, signIn) { feed, prompt -> Session(feed.toAccount(prompt), feed.toBoard()) }

    private val daysPlayed: Flow<Int> = observeGameHistory()
        .map { days -> days.filter { it.record != null && it.puzzleNumber != null }.map { it.day }.distinct().size }
        .distinctUntilChanged()

    private val gameRows: Flow<List<GameRow>> =
        if (seedRows.isEmpty()) {
            flowOf(emptyList())
        } else {
            val perGame = seedRows.map { row ->
                observeGameStats(row.gameId).map { row.copy(solved = it.solved, best = it.best, rankOrder = it.rankOrder) }
            }
            combine(perGame) { it.toList() }
        }

    private val initialAccount: ProfileUiState.Account = identityService.current.value
        ?.let { ProfileUiState.Account.SignedIn(it) }
        ?: signIn.value

    /** The pick (if any) with its board still Loading. */
    private val initialBoard: PerGameBoard = run {
        val picked = selectedBoardGame(savedStateHandle.get<String>(SELECTED_GAME_KEY)?.let(::GameId), LatestSolved.Pending, gameIds)
        PerGameBoard(
            picked,
            rankOrderOf(picked ?: featuredId),
            if (initialAccount is ProfileUiState.Account.SignedIn) DailyBoardState.Loading else DailyBoardState.SignedOut,
        )
    }

    val uiState: StateFlow<ProfileUiState> =
        combine(session, observeGameStats(), daysPlayed, gameRows, preferences.themeMode) { session, stats, days, games, theme ->
            ProfileUiState(session.account, stats, days, games, theme, session.board)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(account = initialAccount, games = seedRows, perGameBoard = initialBoard),
        )

    /** An explicit board pick (A2): it wins over the default from now on and survives process death. */
    fun onSelectGame(game: GameId) {
        savedStateHandle[SELECTED_GAME_KEY] = game.value
    }

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
