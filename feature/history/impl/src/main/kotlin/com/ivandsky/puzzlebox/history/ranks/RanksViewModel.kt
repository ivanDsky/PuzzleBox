package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import com.ivandsky.puzzlebox.gameui.board.toDailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The Ranks hub's state holder: one [selection] (what the player picked) and the [uiState]
 * derived from it, slice by slice.
 *
 * The window's boards are read once, here: every listener the hub holds hangs off [windowBoards]
 * (A23), and Overall derives from it without a read of its own. A game's board is still the
 * first-shipped today board; the picked day and per-game windows join the shared flow later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RanksViewModel(
    private val leaderboard: ObserveLeaderboard,
    private val observeRankWindow: ObserveRankWindow,
    private val observeOverall: ObserveOverall,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val _selection = MutableStateFlow(RanksSelection())
    val selection: StateFlow<RanksSelection> = _selection.asStateFlow()

    private val signInHolder = RanksSignInHolder(identity, idTokens)
    private val viewer = identity.current
    private val games: List<GameId> = rankBoards(registry).filterIsInstance<RanksBoard.Game>().map { it.id }

    /** The window the hub reads: the picked day, or the latest "today" of any game, following the rollover. */
    private val window: Flow<RankWindow> = selection
        .map { it.window to it.day }
        .distinctUntilChanged()
        .flatMapLatest { (kind, day) -> if (day != null) flowOf(kind.at(day)) else today().map(kind::at) }
        .distinctUntilChanged()

    /**
     * The one collection of the window's boards, tagged with the window it answers so a switch
     * never shows the old window's boards as the new one's. Null until the first answer.
     */
    private val windowBoards: StateFlow<Pair<RankWindow, RankWindowBoards>?> = window
        .flatMapLatest { window -> observeRankWindow(window, games).map { window to it } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<RanksUiState> = combine(
        selection.map { it.board to it.window }.distinctUntilChanged().flatMapLatest { (board, kind) -> boardSlice(board, kind) },
        signInHolder.status,
        ::RanksUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        RanksUiState(overallSlice(RanksSelection.Window.Day, OverallState.Loading, null)),
    )

    /** Applies one of [RanksSelection]'s transitions; the policy stays in the value. */
    fun select(change: (RanksSelection) -> RanksSelection) {
        _selection.update(change)
    }

    /** The sign-in prompt's button; [activityContext] hosts Google's account picker. */
    suspend fun signIn(activityContext: Context) = signInHolder.signIn(activityContext)

    private fun today(): Flow<LocalDate> =
        if (games.isEmpty()) flowOf(LocalDate.now(clock)) else combine(games.map(calendar::observeToday)) { days -> days.max() }

    private fun boardSlice(board: RanksBoard, kind: RanksSelection.Window): Flow<RanksBoardSlice> = when (board) {
        RanksBoard.Overall -> overall(kind)
        is RanksBoard.Game -> leaderboard.today(board.id).map { gameSlice(board.id, it.toDailyBoardState()) }
    }

    private fun overall(kind: RanksSelection.Window): Flow<RanksBoardSlice> = window.flatMapLatest { window ->
        val boards = windowBoards.filterNotNull().filter { (answered, _) -> answered == window }.map { (_, boards) -> boards }
        combine(observeOverall(boards, window), viewer) { state, me -> overallSlice(kind, state, me?.uid) }
    }

    private fun overallSlice(kind: RanksSelection.Window, state: OverallState, selfUid: String?) = RanksBoardSlice(
        title = when (kind) {
            RanksSelection.Window.Day -> dailyBoardTitle("Overall")
            RanksSelection.Window.Week -> "Overall · this week"
            RanksSelection.Window.Month -> "Overall · this month"
        },
        order = RankOrder.TimeThenHints,
        state = state.toDailyBoardState(selfUid),
        caption = OVERALL_CAPTION,
        note = state.boardNote(),
    )

    private fun gameSlice(game: GameId, state: DailyBoardState) = RanksBoardSlice(
        title = dailyBoardTitle(registry.displayName(game)),
        order = registry.rankOrderOf(game),
        state = state,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val OVERALL_CAPTION = "Points from every game's board. Higher is better."
    }
}
