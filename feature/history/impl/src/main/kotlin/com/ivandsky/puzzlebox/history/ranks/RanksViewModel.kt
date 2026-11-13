package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.DayReplays
import com.ivandsky.puzzlebox.history.DayReplaysLoader
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.historyRows
import com.ivandsky.puzzlebox.history.solvedByDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    private val observeFriendUids: ObserveFriendUids,
    observeGameHistory: ObserveGameHistory,
    observeGameStats: ObserveGameStats,
    private val playRegistry: GamePlayRegistry,
    loadReplay: LoadSolveReplay,
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

    private val history: Flow<List<HistoryDayRow>> = observeGameHistory()
        .map { historyRows(it, registry, playRegistry, currentYear = LocalDate.now(clock).year) }
        .distinctUntilChanged()

    private val dayReplays = DayReplaysLoader(viewModelScope, loadReplay)

    /** The calendar over local history alone: it renders signed out and offline, and never waits on the network. */
    private val calendarState: Flow<CalendarState> = combine(
        history,
        selection.map { Triple(it.day, it.shownDay, it.calendarExpanded) }.distinctUntilChanged(),
        today(),
    ) { rows, (picked, shown, expanded), today ->
        val earliest = rows.minOfOrNull { it.day }?.coerceAtMost(today) ?: today
        CalendarState(
            shownDay = (shown ?: picked ?: today).coerceIn(earliest, today),
            expanded = expanded,
            earliestDay = earliest,
            selectedDay = picked ?: today,
            solvedByDay = solvedByDay(rows, games),
            daysWithRows = rows.mapTo(HashSet()) { it.day },
            gamesPerDay = games.size,
            today = today,
        )
    }

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

    private val local = combine(calendarState, observeGameStats(), yourSolve) { calendar, stats, solve -> Local(calendar, stats, solve) }

    /** Friends' uids, listened to only while the scope is Friends; the boards never see the scope, so changing it re-reads nothing. */
    private val friends: Flow<Set<String>> = selection.map { it.scope }.distinctUntilChanged()
        .flatMapLatest { scope -> if (scope == RankScope.Friends) observeFriendUids() else flowOf(emptySet()) }

    private val scoped: Flow<RanksBoardSlice> = combine(
        selection.map { Triple(it.board, it.window, it.day) }.distinctUntilChanged().flatMapLatest { (board, kind, day) -> boardSlice(board, kind, day) },
        selection.map { it.scope }.distinctUntilChanged(),
        friends,
    ) { slice, scope, friends ->
        if (scope == RankScope.Friends) slice.copy(state = slice.state.friendsOnly(friends)) else slice
    }

    val uiState: StateFlow<RanksUiState> = combine(
        scoped,
        signInHolder.status,
        selection.map { boards.indexOf(it.board) }.distinctUntilChanged(),
        local,
        selection.map { it.scope to it.window }.distinctUntilChanged(),
    ) { slice, signIn, position, local, (scope, kind) ->
        RanksUiState(slice, signIn, position, boards.size, local.calendar, local.stats, local.yourSolve, scope, kind)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        RanksUiState(overallSlice(RanksSelection.Window.Day, null, OverallState.Loading, null)),
    )

    /** Applies one of [RanksSelection]'s transitions; the policy stays in the value. */
    fun select(change: (RanksSelection) -> RanksSelection) {
        _selection.update(change)
    }

    /** The tune sheet's scope pick. */
    fun setScope(scope: RankScope) {
        select { it.setScope(scope) }
    }

    /** The tune sheet's window pick: Week and Month anchor at the picked day, else today. */
    fun setWindow(window: RanksSelection.Window) {
        select { it.setWindow(window) }
    }

    /** A swipe on the board: [delta] boards along the hub's boards, not past either end. */
    fun swipe(delta: Int) {
        select { it.step(delta, boards) }
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

    private fun overall(kind: RanksSelection.Window, day: LocalDate?): Flow<RanksBoardSlice> = window.flatMapLatest { window ->
        val answered = windowBoards.filterNotNull().filter { (of, _) -> of == window }.map { (_, boards) -> boards }
        combine(observeOverall(answered, window), viewer) { state, me -> overallSlice(kind, day, state, me?.uid) }
    }

    private fun game(game: GameId, kind: RanksSelection.Window, day: LocalDate?): Flow<RanksBoardSlice> = window.flatMapLatest { window ->
        combine(windowBoards, viewer) { answered, me ->
            val inWindow = answered?.takeIf { (of, _) -> of == window }?.second
            RanksBoardSlice(
                title = boardTitle(registry.displayName(game), kind, day),
                order = registry.rankOrderOf(game),
                state = inWindow.toGameBoardState(game, registry.rankOrderOf(game), kind, me?.uid),
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

    /** A picked day names its day, week (by its Monday) or month; no pick is the live "today" / "this week" / "this month". */
    private fun boardTitle(name: String, kind: RanksSelection.Window, day: LocalDate?) = when {
        day == null -> when (kind) {
            RanksSelection.Window.Day -> dailyBoardTitle(name)
            RanksSelection.Window.Week -> "$name · this week"
            RanksSelection.Window.Month -> "$name · this month"
        }
        kind == RanksSelection.Window.Day -> "$name · ${day.format(DAY_LABEL)}"
        kind == RanksSelection.Window.Week -> "$name · week of ${day.with(DayOfWeek.MONDAY).format(DAY_LABEL)}"
        else -> "$name · ${day.format(MONTH_LABEL)}"
    }

    /** The replays of [row]'s day only: a switch never flashes the last day's. */
    private fun DayReplays.of(row: HistoryDayRow): HistoryReplays = if (key == row.key) replays else HistoryReplays.Hidden

    private data class Local(val calendar: CalendarState, val stats: GameStats, val yourSolve: YourSolve?)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val OVERALL_CAPTION = "Points from every game's board. Higher is better."
        val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
        val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
