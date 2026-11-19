package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.TopBarFlag
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.DailyRepository
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
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.DayReplays
import com.ivandsky.puzzlebox.history.DayReplaysLoader
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.flags.HistoryFlag
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The Ranks hub's state holder: one [selection] (what the player picked) and the [uiState]
 * derived from it, slice by slice.
 *
 * The window's boards are read once, here: every listener the hub holds hangs off [reads]
 * and Overall and each game's board are projections of it in either scope, so a swipe between boards
 * or a scope change attaches and fetches nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RanksViewModel(
    observeRankWindow: ObserveRankWindow,
    observeWindowRefresh: ObserveWindowRefresh,
    observeOverall: ObserveOverall,
    observeFriendUids: ObserveFriendUids,
    observeGameHistory: ObserveGameHistory,
    observeGameStats: ObserveGameStats,
    private val playRegistry: GamePlayRegistry,
    loadReplay: LoadSolveReplay,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val preferences: UserPreferencesRepository,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
    private val daily: DailyRepository,
    private val clock: Clock = Clock.systemUTC(),
    linkedIn: ObserveLinkedInDayView? = null,
    releaseFlags: ReleaseFlags,
    savedState: SavedStateHandle = SavedStateHandle(),
    opening: RanksSelection = RanksSelection(),
) : ViewModel() {

    private val saver = RanksSelectionSaver(savedState)
    private val landing = RanksLanding(preferences, observeFriendUids().map { it.isNotEmpty() }, viewModelScope)
    private val _selection = MutableStateFlow(saver.restore(opening, rankBoards(registry)))
    val selection: StateFlow<RanksSelection> = _selection.asStateFlow()

    /** False until a fresh hub has its landing scope; a restored selection has nothing to wait for. */
    private val landed = MutableStateFlow(saver.hasSaved)

    init {
        if (!landed.value) {
            viewModelScope.launch {
                val initial = _selection.value
                val stored = landing.stored.first()
                // A scope picked meanwhile wins over the landing scope.
                _selection.update { if (it == initial) it.setScope(stored) else it }
                landed.value = true
            }
        }
    }

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

    private val reads = RanksWindowReads(observeRankWindow, observeWindowRefresh, window, games, viewModelScope)
    private val scopedBoards = RanksScopedBoards(reads.boards, observeFriendUids, viewer, viewModelScope)

    /** The Tune sheet shows the board's choices only: the Defaults block stays in Settings (RanksTuneBoardOnly). */
    val tuneBoardOnly: Boolean = releaseFlags.isEnabled(HistoryFlag.RanksTuneBoardOnly)

    /** The tab's hub on Today's large top bar (MainTabsLargeBar), read once like [tuneBoardOnly]. */
    private val largeBar = releaseFlags.isEnabled(TopBarFlag.MainTabsLargeBar)
    /** A game's Week and Month add up its daily place points (WeekMonthPoints), read once the same way. */
    private val weekMonthPoints = releaseFlags.isEnabled(HistoryFlag.WeekMonthPoints)

    // The LinkedIn part of the hub (the line, the pull's sync) exists only with RanksFreshness on and LinkedIn in the graph.
    private val freshLinkedIn = linkedIn.takeIf { releaseFlags.isEnabled(HistoryFlag.RanksFreshness) }
    private val freshness = freshLinkedIn?.let { RanksFreshness(clock) }
    private val pullSync = freshLinkedIn?.let { RanksPullSync(it, viewModelScope, ::shownLinkedInDay) }

    /** The pull-to-refresh indicator: a pull's own brief one, held while the pull's LinkedIn sync runs when there is one. */
    val refreshing: StateFlow<Boolean> = pullSync?.let { sync ->
        combine(reads.refreshing, sync.syncing) { pulling, syncing -> pulling || syncing }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
    } ?: reads.refreshing

    fun refresh() {
        reads.pull()
        pullSync?.pull()
    }

    /** The game day whose LinkedIn part the board shows: a game's Day board in Friends; no other board has one. */
    private suspend fun shownLinkedInDay(): ObserveLinkedInDayView.Target? {
        val picked = selection.value
        val board = picked.board as? RanksBoard.Game ?: return null
        if (picked.window != RanksSelection.Window.Day || picked.scope != RankScope.Friends) return null
        return ObserveLinkedInDayView.Target(board.id, picked.day ?: today().first())
    }

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
            rows.solvedOn(board, picked ?: today)
        }
    }.distinctUntilChanged().onEach(dayReplays::open).flatMapLatest { row ->
        if (row == null) flowOf(null) else dayReplays.replays.map { YourSolve(row, it.of(row), playRegistry[row.gameId]) }
    }

    /** The viewer's solved row of [board]'s game on [day], the one rule behind both Your solve and Play this day. */
    private fun List<HistoryDayRow>.solvedOn(board: RanksBoard.Game, day: LocalDate): HistoryDayRow? =
        firstOrNull { it.gameId == board.id && it.day == day && it.record != null }

    /** What Play this day opens, if anything: the game board's day when the viewer has no solve of it (see [playThisDay]). */
    private val playThisDay: Flow<PlayThisDay?> = combine(
        history,
        selection.map { Triple(it.board, it.window, it.day) }.distinctUntilChanged(),
        today(),
    ) { rows, (board, kind, picked), today ->
        val day = picked ?: today
        val solved = board is RanksBoard.Game && rows.solvedOn(board, day) != null
        playThisDay(board, kind, day, solved, (board as? RanksBoard.Game)?.let { daily.archiveRange(it.id) }, today)
    }.distinctUntilChanged()

    /** The picked game's own stats; Overall has none, so no game's best time can stand for another's. */
    private val stats: Flow<GameStats?> = selection.map { it.board }.distinctUntilChanged().flatMapLatest { board ->
        if (board is RanksBoard.Game) observeGameStats(board.id) else flowOf(null)
    }

    private val local = combine(calendarState, stats, yourSolve, nudge, playThisDay, ::Local)

    private val slices = RanksBoardSlices(boards, window, scopedBoards::of, observeOverall, viewer, history, calendar, registry, { daily.linkedInGameTypeId(it) != null }, BoardGhosts(linkedIn), freshness, weekMonthPoints)

    /**
     * Every board as its slot draws it in the picked scope, with that scope: the one a swipe scrubs to is already
     * there, and the scope the hub names is always the one its boards and places answer. In Friends they carry
     * the LinkedIn connections the phone holds.
     */
    private val scoped: Flow<Pair<RankScope, List<RanksBoardSlice>>> = landed.filter { it }.flatMapLatest {
        selection.map { Triple(it.window, it.day, it.scope) }.distinctUntilChanged()
    }.flatMapLatest { (kind, day, scope) -> slices.observe(kind, day, scope).map { scope to it } }

    val uiState: StateFlow<RanksUiState> = combine(
        scoped,
        signInHolder.status,
        selection.map { boards.indexOf(it.board) }.distinctUntilChanged(),
        local,
        selection.map { it.window }.distinctUntilChanged(),
    ) { (scope, slices), signIn, position, local, kind ->
        val slice = slices.getOrElse(position) { slices.first() }
        val places = boardPlaces(boards, registry, playRegistry::brandOf, slices)
        RanksUiState(slice, signIn, position, boards.size, local.calendar, local.stats, local.yourSolve, scope, kind, places, local.nudge, slices, local.playThisDay, largeBar, weekMonthPoints)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        RanksUiState(overallSlice(RanksSelection.Window.Day, null, OverallState.Loading, null), largeBar = largeBar, weekMonthPoints = weekMonthPoints),
    )

    /** Applies one of [RanksSelection]'s transitions; the policy stays in the value. */
    fun select(change: (RanksSelection) -> RanksSelection) {
        saver.save(_selection.updateAndGet(change))
    }

    /** A second tab reselect: back to the landing choice, on the player's default scope. */
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

    /** The replays of [row]'s day only: a switch never flashes the last day's. */
    private fun DayReplays.of(row: HistoryDayRow): HistoryReplays = if (key == row.key) replays else HistoryReplays.Hidden

    private data class Local(val calendar: CalendarState, val stats: GameStats?, val yourSolve: YourSolve?, val nudge: Boolean, val playThisDay: PlayThisDay?)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val SWIPE_NUDGE = GameId("ranks.swipe")
    }
}
