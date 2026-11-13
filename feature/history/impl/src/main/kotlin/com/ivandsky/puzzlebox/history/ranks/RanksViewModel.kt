package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
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
 * (A23), and Overall and each game's board are projections of it, so a swipe between boards
 * attaches and fetches nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RanksViewModel(
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
    private val boards = rankBoards(registry)
    private val games: List<GameId> = boards.filterIsInstance<RanksBoard.Game>().map { it.id }

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
        selection.map { boards.indexOf(it.board) }.distinctUntilChanged(),
    ) { slice, signIn, position -> RanksUiState(slice, signIn, position, boards.size) }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        RanksUiState(overallSlice(RanksSelection.Window.Day, OverallState.Loading, null)),
    )

    /** Applies one of [RanksSelection]'s transitions; the policy stays in the value. */
    fun select(change: (RanksSelection) -> RanksSelection) {
        _selection.update(change)
    }

    /** A swipe on the board: [delta] boards along the hub's boards, not past either end. */
    fun swipe(delta: Int) {
        select { it.step(delta, boards) }
    }

    /** The sign-in prompt's button; [activityContext] hosts Google's account picker. */
    suspend fun signIn(activityContext: Context) = signInHolder.signIn(activityContext)

    private fun today(): Flow<LocalDate> =
        if (games.isEmpty()) flowOf(LocalDate.now(clock)) else combine(games.map(calendar::observeToday)) { days -> days.max() }

    private fun boardSlice(board: RanksBoard, kind: RanksSelection.Window): Flow<RanksBoardSlice> = when (board) {
        RanksBoard.Overall -> overall(kind)
        is RanksBoard.Game -> game(board.id, kind)
    }

    private fun overall(kind: RanksSelection.Window): Flow<RanksBoardSlice> = window.flatMapLatest { window ->
        val answered = windowBoards.filterNotNull().filter { (of, _) -> of == window }.map { (_, boards) -> boards }
        combine(observeOverall(answered, window), viewer) { state, me -> overallSlice(kind, state, me?.uid) }
    }

    private fun game(game: GameId, kind: RanksSelection.Window): Flow<RanksBoardSlice> = window.flatMapLatest { window ->
        combine(windowBoards, viewer) { answered, me ->
            val inWindow = answered?.takeIf { (of, _) -> of == window }?.second
            RanksBoardSlice(
                title = boardTitle(registry.displayName(game), kind),
                order = registry.rankOrderOf(game),
                state = inWindow.toGameBoardState(game, registry.rankOrderOf(game), kind, me?.uid),
                note = inWindow.gameBoardNote(game, kind),
            )
        }
    }

    private fun overallSlice(kind: RanksSelection.Window, state: OverallState, selfUid: String?) = RanksBoardSlice(
        title = boardTitle("Overall", kind),
        order = RankOrder.TimeThenHints,
        state = state.toDailyBoardState(selfUid),
        caption = OVERALL_CAPTION,
        note = state.boardNote(),
    )

    private fun boardTitle(name: String, kind: RanksSelection.Window) = when (kind) {
        RanksSelection.Window.Day -> dailyBoardTitle(name)
        RanksSelection.Window.Week -> "$name · this week"
        RanksSelection.Window.Month -> "$name · this month"
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val OVERALL_CAPTION = "Points from every game's board. Higher is better."
    }
}
