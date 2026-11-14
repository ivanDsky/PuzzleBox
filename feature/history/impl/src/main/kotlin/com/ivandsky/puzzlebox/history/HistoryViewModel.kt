package com.ivandsky.puzzlebox.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.restoring
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * Everything History renders besides the paged list: every game's full [rows]
 * (selection, best and day-filter lookups must see every day, not just the loaded
 * pages) and the All [stats] (null until the first emission).
 *
 * The calendar shows the week — or, [calendarExpanded], the month — holding
 * [calendarDay] (null until the first emission), between [earliestDay] (History's
 * oldest row) and [today]. [solvedByDay] holds each day's numbered solves as brands,
 * [gamesPerDay] ring segments a day (every registered game) and [daysWithRows] the days
 * with any row (solved or not). [selectedDay] filters the list to one day. [today] is
 * the puzzle day the list's "Today" / "Yesterday" count from. [openingRowKey] is the row a
 * game's archive link asked for, null when the key names no game or that day has no such row.
 * [restoring] is true while the cloud's first restore on this device runs (A34): the empty list
 * says history is coming back instead of the first-time copy.
 */
data class HistoryUiState(
    val rows: List<HistoryDayRow> = emptyList(),
    val stats: GameStats? = null,
    val calendarDay: LocalDate? = null,
    val calendarExpanded: Boolean = false,
    val earliestDay: LocalDate? = null,
    val selectedDay: LocalDate? = null,
    val solvedByDay: Map<LocalDate, List<GameBrand>> = emptyMap(),
    val daysWithRows: Set<LocalDate> = emptySet(),
    val gamesPerDay: Int = 0,
    val today: LocalDate? = null,
    val openingRowKey: String? = null,
    val restoring: Boolean = false,
) {
    /** The calendar's slice of this state. */
    fun toCalendarState() = CalendarState(
        shownDay = calendarDay,
        expanded = calendarExpanded,
        earliestDay = earliestDay,
        selectedDay = selectedDay,
        solvedByDay = solvedByDay,
        daysWithRows = daysWithRows,
        gamesPerDay = gamesPerDay,
        today = today,
    )
}

/**
 * History's state holder: cache-only reads over the GAME-AGNOSTIC merged history
 * ([ObserveGameHistory] — every game's completion records UNION every registered game's
 * captured days; fix round 5, defect C). Rows render per GameId, named by :app's
 * [GameRegistry] and branded by [GamePlayRegistry] (the same lookups Home uses). The
 * list pages lazily from the same merged emissions the state is built from — a paging
 * generation is never built from a pre-emission empty list.
 *
 * History is the All timeline only (spec A28): per-game pages live in Play's library.
 * The stats are [ObserveGameStats] across every game, the same numbers Profile and
 * Result show (practice never counts), and each game's best row is its best numbered
 * solve under its ranking — the row those stats' best reads.
 *
 * Calendar: [showCalendar] picks the shown week or month and the mode (clamped in the
 * state to the days with history, never past today) and [selectDay] toggles a one-day
 * filter. Both live here, so a rotation keeps the calendar, the filter and the rows
 * they are clamped to together.
 *
 * Opening: a [HistoryKey] with a game starts filtered to its day (today when it has none) with
 * the calendar there; [HistoryUiState.openingRowKey] names that game's row for the day, for
 * layouts that open a detail pane.
 *
 * Replays: the list never loads them. [openReplays] loads the open day's record and latest
 * solves into [replays] ([HistoryReplays.Unavailable] when neither has one); closing the detail
 * drops them.
 *
 * Play again is navigation only (ticket 11): the route reports the (game, day) and the play
 * store opens that day's slot — History never builds a session blob.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    observeGameHistory: ObserveGameHistory,
    observeGameStats: ObserveGameStats,
    private val registry: GameRegistry,
    private val playRegistry: GamePlayRegistry,
    private val puzzleDay: GetPuzzleDay,
    private val loadReplay: LoadSolveReplay,
    cloudSync: CloudSync,
    opening: HistoryKey = HistoryKey(),
) : ViewModel() {

    private val order: List<GameId> = registry.all.map { it.definition.id }
    private val openingGame: GameId? = opening.gameId?.let { GameId(it) }
    private val openingDay: LocalDate? = openingGame?.let {
        (opening.day?.let(LocalDate::parse) ?: puzzleDay()).coerceAtMost(puzzleDay())
    }
    private val calendar = MutableStateFlow(CalendarView(openingDay ?: puzzleDay(), expanded = false))
    private val day = MutableStateFlow(openingDay)

    private val rows: Flow<List<HistoryDayRow>> = observeGameHistory()
        .map { merged -> historyRows(merged, registry, playRegistry, currentYear = puzzleDay().year) }
        .distinctUntilChanged().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val restoring: Flow<Boolean> = cloudSync.restoring

    val uiState: StateFlow<HistoryUiState> = combine(
        rows,
        observeGameStats(),
        calendar,
        day,
        restoring,
    ) { rows, stats, (wanted, wide), selected, restoring ->
        val today = puzzleDay()
        val earliest = rows.minOfOrNull { it.day }?.coerceAtMost(today) ?: today
        HistoryUiState(
            rows = rows,
            stats = stats,
            calendarDay = wanted.coerceIn(earliest, today),
            calendarExpanded = wide,
            earliestDay = earliest,
            selectedDay = selected,
            solvedByDay = solvedByDay(rows, order),
            daysWithRows = rows.mapTo(HashSet()) { it.day },
            gamesPerDay = order.size,
            today = today,
            restoring = restoring,
            openingRowKey = openingGame?.let { game ->
                rows.firstOrNull { it.gameId == game && it.day == openingDay }?.key
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    // Rows only: a stats-only emission must not start a new paging generation.
    val pagedDays: Flow<PagingData<HistoryDayRow>> = rows.flatMapLatest { historyPager(it) }.cachedIn(viewModelScope)

    private val dayReplays = DayReplaysLoader(viewModelScope, loadReplay)

    /** [game]'s play UI, which owns its replay board; null for a game this build has no play UI for. */
    fun playUiOf(game: GameId): GamePlayUi? = playRegistry[game]

    /** The replays of the open day. */
    val replays: StateFlow<DayReplays> = dayReplays.replays

    /** Loads the replays of [row]'s solve; null (the detail closed), or a row without one, drops them. */
    fun openReplays(row: HistoryDayRow?) = dayReplays.open(row)

    /**
     * Shows the week holding [day] — the month holding it when [expanded]; the state
     * clamps [day] between the oldest row's day and today.
     */
    fun showCalendar(day: LocalDate, expanded: Boolean) {
        calendar.value = CalendarView(day, expanded)
    }

    /**
     * Filters the list to [date]; the selected day again (or null) clears the filter. A day
     * after today is ignored — nothing can have been played there.
     */
    fun selectDay(date: LocalDate?) {
        day.value = when {
            date == null || date == day.value -> null
            date > puzzleDay() -> day.value
            else -> date
        }
    }

    /** The calendar's wanted day (its week or month is shown) and mode, set together. */
    private data class CalendarView(val day: LocalDate, val expanded: Boolean)
}
