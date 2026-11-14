package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.inSubmitWindow
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.domain.ranks.ObserveWindowRefresh
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.DayReplays
import com.ivandsky.puzzlebox.history.DayReplaysLoader
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.historyRows
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

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
    observeRankWindow: ObserveRankWindow,
    observeWindowRefresh: ObserveWindowRefresh,
    private val observeOverall: ObserveOverall,
    private val observeFriendUids: ObserveFriendUids,
    observeGameHistory: ObserveGameHistory,
    observeGameStats: ObserveGameStats,
    private val playRegistry: GamePlayRegistry,
    loadReplay: LoadSolveReplay,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val preferences: UserPreferencesRepository,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
    private val clock: Clock = Clock.systemUTC(),
    linkedIn: ObserveLinkedInDayView? = null,
    savedState: SavedStateHandle = SavedStateHandle(),
    opening: RanksSelection = RanksSelection(),
) : ViewModel() {

    private val saver = RanksSelectionSaver(savedState)
    private val landing = RanksLanding(preferences, viewModelScope)
    private val _selection = MutableStateFlow(saver.restore(opening.setScope(landing.scope.value), rankBoards(registry)))
    val selection: StateFlow<RanksSelection> = _selection.asStateFlow()

    private val signInHolder = RanksSignInHolder(identity, idTokens)
    private val ghosts = BoardGhosts(linkedIn)
    private val viewer = identity.current
    private val boards = rankBoards(registry)
    private val games: List<GameId> = boards.filterIsInstance<RanksBoard.Game>().map { it.id }

    /** The window the hub reads: the picked day, or the latest "today" of any game, following the rollover. */
    private val window: Flow<RankWindow> = selection
        .map { it.window to it.day }
        .distinctUntilChanged()
        .flatMapLatest { (kind, day) -> if (day != null) flowOf(kind.at(day)) else today().map(kind::at) }
        .distinctUntilChanged()

    private val reads = RanksWindowReads(observeRankWindow, observeWindowRefresh, window, games, viewModelScope)
    private val windowBoards: StateFlow<Pair<RankWindow, RankWindowBoards>?> = reads.boards

    /** The pull-to-refresh indicator, and the pull itself. */
    val refreshing: StateFlow<Boolean> = reads.refreshing

    fun refresh() = reads.pull()

    /** Overall's state over the shared boards, held once: Overall's slice and the title menu both read it. */
    private val overallState: StateFlow<OverallState> = window.flatMapLatest { window ->
        val answered = windowBoards.filterNotNull().filter { (of, _) -> of == window }.map { (_, boards) -> boards }
        observeOverall(answered, window)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OverallState.Loading)

    /** The viewer's place on every board for the title menu: projections of the shared boards, so opening it reads nothing. */
    private val places: Flow<List<BoardPlace>> = combine(
        window,
        windowBoards,
        overallState,
        viewer,
        selection.map { it.window }.distinctUntilChanged(),
    ) { window, answered, overall, me, kind ->
        boardPlaces(boards, registry, playRegistry::brandOf, overall, answered?.takeIf { (of, _) -> of == window }?.second, kind, me?.uid)
    }.distinctUntilChanged()

    private val nudge: Flow<Boolean> = preferences.tutorialSeen(SWIPE_NUDGE).map { !it }

    private val history: Flow<List<HistoryDayRow>> = observeGameHistory()
        .map { historyRows(it, registry, playRegistry, currentYear = LocalDate.now(clock).year) }
        .distinctUntilChanged()

    private val dayReplays = DayReplaysLoader(viewModelScope, loadReplay)

    private val calendarState: Flow<CalendarState> = calendarStateOf(history, selection, today(), games)

    /** The viewer's solve of the game board's day: only on a game's Day board, and only once solved. */
    private val yourSolve: Flow<YourSolve?> = combine(
        history,
        selection.map { Triple(it.board, it.window, it.day) }.distinctUntilChanged(),
        today(),
    ) { rows, (board, kind, picked), today ->
        if (board !is RanksBoard.Game || kind != RanksSelection.Window.Day) {
            null
        } else {
            rows.firstOrNull { it.gameId == board.id && it.day == (picked ?: today) && it.record != null }
        }
    }.distinctUntilChanged().onEach(dayReplays::open).flatMapLatest { row ->
        if (row == null) flowOf(null) else dayReplays.replays.map { YourSolve(row, it.of(row), playRegistry[row.gameId]) }
    }

    private val local = combine(calendarState, observeGameStats(), yourSolve, places, nudge) { calendar, stats, solve, places, nudge ->
        Local(calendar, stats, solve, places, nudge)
    }

    /** Friends' uids, listened to only while the scope is Friends; the boards never see the scope, so changing it re-reads nothing. */
    private val friends: Flow<Set<String>> = selection.map { it.scope }.distinctUntilChanged()
        .flatMapLatest { scope -> if (scope == RankScope.Friends) observeFriendUids() else flowOf(emptySet()) }

    /** The LinkedIn ghosts of the board on screen: read only on a game's Day board in Friends scope. */
    private val ghostInput: Flow<BoardGhostInput> = combine(selection.map { it.board to it.scope }.distinctUntilChanged(), window) { (board, scope), window ->
        Triple(board, scope, window)
    }.flatMapLatest { (board, scope, window) -> ghosts.observe(board, scope, window) }

    private val scoped: Flow<RanksBoardSlice> = combine(
        selection.map { Triple(it.board, it.window, it.day) }.distinctUntilChanged().flatMapLatest { (board, kind, day) -> boardSlice(board, kind, day) },
        selection.map { it.scope }.distinctUntilChanged(),
        friends,
        ghostInput,
    ) { slice, scope, friends, ghostInput ->
        if (scope == RankScope.Friends) slice.inFriends(friends, ghostInput) else slice
    }

    val uiState: StateFlow<RanksUiState> = combine(
        scoped,
        signInHolder.status,
        selection.map { boards.indexOf(it.board) }.distinctUntilChanged(),
        local,
        selection.map { it.scope to it.window }.distinctUntilChanged(),
    ) { slice, signIn, position, local, (scope, kind) ->
        RanksUiState(slice, signIn, position, boards.size, local.calendar, local.stats, local.yourSolve, scope, kind, local.places, local.nudge)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        RanksUiState(overallSlice(RanksSelection.Window.Day, null, OverallState.Loading, null)),
    )

    /** Applies one of [RanksSelection]'s transitions; the policy stays in the value. */
    fun select(change: (RanksSelection) -> RanksSelection) {
        saver.save(_selection.updateAndGet(change))
    }

    /** A second tab reselect: back to the landing choice, on the player's default scope (A4, A8, F1). */
    fun resetSelection() {
        select { it.reset(landing.scope.value) }
    }

    /** The tune sheet's scope pick. */
    fun setScope(scope: RankScope) {
        select { it.setScope(scope) }
    }

    /** The tune sheet's window pick: Week and Month anchor at the picked day, else today. */
    fun setWindow(window: RanksSelection.Window) {
        select { it.setWindow(window) }
    }

    /** A swipe on the board: [delta] boards along the hub's boards, not past either end. A swipe is the lesson the nudge teaches. */
    fun swipe(delta: Int) {
        select { it.step(delta, boards) }
        markSwipeNudgeSeen()
    }

    /** The title menu's pick: that board, re-ranked in place. */
    fun pickBoard(board: RanksBoard) {
        select { it.pickBoard(board) }
    }

    /** The one-time swipe hint has played (or the player swiped): never show it again. */
    fun markSwipeNudgeSeen() {
        viewModelScope.launch { preferences.markTutorialSeen(SWIPE_NUDGE) }
    }

    /** The calendar's week or month moved: show the one holding [day]. The picked day stays. */
    fun showCalendar(day: LocalDate, expanded: Boolean) {
        select { it.showCalendar(day, expanded) }
    }

    /** A day tapped on the calendar re-ranks the board for it and folds the month back to its week; today is "no day picked". */
    fun selectDay(date: LocalDate?) {
        val today = uiState.value.calendar.today
        val picked = date.takeUnless { it == today }
        select { it.pickFromCalendar(picked) }
    }

    /** The sign-in prompt's button; [activityContext] hosts Google's account picker. */
    suspend fun signIn(activityContext: Context) = signInHolder.signIn(activityContext)

    private fun today(): Flow<LocalDate> =
        if (games.isEmpty()) flowOf(LocalDate.now(clock)) else combine(games.map(calendar::observeToday)) { days -> days.max() }

    private fun boardSlice(board: RanksBoard, kind: RanksSelection.Window, day: LocalDate?): Flow<RanksBoardSlice> = when (board) {
        RanksBoard.Overall -> overall(kind, day)
        is RanksBoard.Game -> game(board.id, kind, day)
    }

    private fun overall(kind: RanksSelection.Window, day: LocalDate?): Flow<RanksBoardSlice> =
        combine(overallState, viewer) { state, me -> overallSlice(kind, day, state, me?.uid) }

    private fun game(game: GameId, kind: RanksSelection.Window, day: LocalDate?): Flow<RanksBoardSlice> = window.flatMapLatest { window ->
        combine(windowBoards, viewer, history, calendar.observeToday(game)) { answered, me, rows, _ ->
            val inWindow = answered?.takeIf { (of, _) -> of == window }?.second
            val order = registry.rankOrderOf(game)
            val unofficial = inWindow.unofficialRow(game, order, me, rows) { calendar.inSubmitWindow(game, it) }.takeIf { kind == RanksSelection.Window.Day }
            RanksBoardSlice(
                title = boardTitle(registry.displayName(game), kind, day),
                order = order,
                state = inWindow.toGameBoardState(game, order, kind, me?.uid).withUnofficialRow(unofficial),
                note = inWindow.gameBoardNote(game, kind),
            )
        }
    }

    private fun overallSlice(kind: RanksSelection.Window, day: LocalDate?, state: OverallState, selfUid: String?) = RanksBoardSlice(
        title = boardTitle("Overall", kind, day),
        order = RankOrder.TimeThenHints,
        state = state.toDailyBoardState(selfUid),
        caption = OVERALL_CAPTION,
        note = state.boardNote(),
    )

    /** The replays of [row]'s day only: a switch never flashes the last day's. */
    private fun DayReplays.of(row: HistoryDayRow): HistoryReplays = if (key == row.key) replays else HistoryReplays.Hidden

    private data class Local(val calendar: CalendarState, val stats: GameStats, val yourSolve: YourSolve?, val places: List<BoardPlace>, val nudge: Boolean)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val OVERALL_CAPTION = "Points from every game's board. Higher is better."
        val SWIPE_NUDGE = GameId("ranks.swipe")
    }
}
