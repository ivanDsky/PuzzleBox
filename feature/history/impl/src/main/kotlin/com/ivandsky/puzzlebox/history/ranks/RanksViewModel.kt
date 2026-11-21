package com.ivandsky.puzzlebox.history.ranks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendBoards
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.history.domain.ranks.ObserveWindowRefresh
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
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
import kotlinx.coroutines.flow.stateIn
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
    friendBoards: ObserveFriendBoards,
    linkedIn: ObserveLinkedInDayView? = null,
    savedState: SavedStateHandle = SavedStateHandle(),
    opening: RanksSelection = RanksSelection(),
) : ViewModel() {

    private val saver = RanksSelectionSaver(savedState)
    private val boards = rankBoards(registry)

    /** The games LinkedIn feeds: the ones the LinkedIn games pick keeps. */
    private val isLinkedInGame: (GameId) -> Boolean = { daily.linkedInGameTypeId(it) != null }
    private val landing = RanksLanding(preferences, observeFriendUids().map { it.isNotEmpty() }, viewModelScope)
    private val _selection = MutableStateFlow(saver.restore(opening, boards))
    val selection: StateFlow<RanksSelection> = _selection.asStateFlow()

    /** False until a fresh hub has its landing scope; a restored selection has nothing to wait for. */
    private val landed = MutableStateFlow(saver.hasSaved)

    init {
        if (!landed.value) {
            viewModelScope.launch {
                landing.land(_selection, ::boardsOf)
                landed.value = true
            }
        }
    }

    private val signInHolder = RanksSignInHolder(identity, idTokens)
    private val viewer = identity.current

    private val games: List<GameId> = boards.filterIsInstance<RanksBoard.Game>().map { it.id }

    /** The boards [games] lists: the swipe, the dots, the title menu and the tablet's list all walk these. */
    private fun boardsOf(games: RanksSelection.Games) = boards.listed(games, isLinkedInGame)

    /** The window the hub reads: the picked day, or the latest "today" of any game, following the rollover. */
    private val window: Flow<RankWindow> = selection
        .map { it.window to it.day }
        .distinctUntilChanged()
        .flatMapLatest { (kind, day) -> if (day != null) flowOf(kind.at(day)) else today().map(kind::at) }
        .distinctUntilChanged()

    private val reads = RanksWindowReads(observeRankWindow, observeWindowRefresh, window, games, viewModelScope)
    private val scopedBoards = RanksScopedBoards(reads.boards, friendBoards, viewModelScope)

    // The LinkedIn part of the hub (the line, the pull's sync) exists only with LinkedIn in the graph.
    private val freshness = linkedIn?.let { RanksFreshness(clock) }
    private val pullSync = linkedIn?.let { RanksPullSync(it, viewModelScope, ::shownLinkedInDay) }

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

    private val calendarState: Flow<CalendarState> = calendarStateOf(history, selection, today(), games)
    private val solves = RanksSolves(history, selection, today(), daily, playRegistry, loadReplay, viewModelScope)

    /** The picked game's own stats; Overall has none, so no game's best time can stand for another's. */
    private val stats: Flow<GameStats?> = selection.map { it.board }.distinctUntilChanged().flatMapLatest { board ->
        if (board is RanksBoard.Game) observeGameStats(board.id) else flowOf(null)
    }

    private val local = combine(calendarState, stats, solves.yourSolve, nudge, solves.playThisDay, ::Local)

    private val slices = RanksBoardSlices(boards, window, scopedBoards::of, observeOverall, viewer, history, calendar, registry, isLinkedInGame, freshness)

    /**
     * Every board as its slot draws it in the picked scope and games, with both: the one a swipe scrubs to is already
     * there, and the scope and games the hub names are always the ones its boards and places answer. In Friends they carry
     * the LinkedIn connections the phone holds.
     */
    private val scoped: Flow<Triple<RankScope, RanksSelection.Games, List<RanksBoardSlice>>> = landed.filter { it }.flatMapLatest {
        selection.map { SliceKey(it.window, it.day, it.scope, it.games) }.distinctUntilChanged()
    }.flatMapLatest { (kind, day, scope, games) -> slices.observe(kind, day, scope, games).map { Triple(scope, games, it) } }

    val uiState: StateFlow<RanksUiState> = combine(
        scoped,
        signInHolder.status,
        selection.map { it.board }.distinctUntilChanged(),
        local,
        selection.map { it.window }.distinctUntilChanged(),
    ) { (scope, games, slices), signIn, board, local, kind ->
        val listed = boardsOf(games)
        val position = listed.indexOf(board).coerceAtLeast(0)
        val slice = slices.getOrElse(position) { slices.first() }
        val places = boardPlaces(listed, registry, playRegistry::brandOf, slices)
        RanksUiState(slice, signIn, position, listed.size, local.calendar, local.stats, local.yourSolve, scope, kind, places, local.nudge, slices, local.playThisDay, games)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        RanksUiState(overallSlice(RanksSelection.Window.Day, null, OverallState.Loading, null), games = _selection.value.games),
    )

    /** Applies one of [RanksSelection]'s transitions; the policy stays in the value. */
    fun select(change: (RanksSelection) -> RanksSelection) {
        saver.save(_selection.updateAndGet(change))
    }

    /** A second tab reselect: back to the landing choice, on the player's default scope and games. */
    fun resetSelection() {
        select { it.reset(landing.scope.value, landing.games.value) }
    }

    /** The Who chip's pick. */
    fun setScope(scope: RankScope) {
        select { it.setScope(scope) }
    }

    /** The When chip's pick: Week and Month anchor at the picked day, else today. */
    fun setWindow(window: RanksSelection.Window) {
        select { it.setWindow(window) }
    }

    /** The Games pick: that board stays when it is still listed, else the hub moves to Overall. */
    fun setGames(games: RanksSelection.Games) {
        select { it.setGames(games, boardsOf(games)) }
    }

    /** A swipe on the board: [delta] boards along the hub's boards, not past either end. A swipe is the lesson the nudge teaches. */
    fun swipe(delta: Int) {
        select { it.step(delta, boardsOf(it.games)) }
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

    private data class Local(val calendar: CalendarState, val stats: GameStats?, val yourSolve: YourSolve?, val nudge: Boolean, val playThisDay: PlayThisDay?)

    /** What re-reads the slices: the window and day, the scope, and the games they count. */
    private data class SliceKey(val kind: RanksSelection.Window, val day: LocalDate?, val scope: RankScope, val games: RanksSelection.Games)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val SWIPE_NUDGE = GameId("ranks.swipe")
    }
}
