package com.ivandsky.puzzlebox.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObservePastDay
import com.ivandsky.puzzlebox.domain.PastDay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * Everything History renders besides the paged list, for the selected tab: the game
 * [tabs], the tab's full [rows] (selection, best and day-filter lookups must see every
 * day, not just the loaded pages), and its [stats] (null until the first emission).
 *
 * The calendar shows the week — or, [calendarExpanded], the month — holding
 * [calendarDay] (null until the first emission), between [earliestDay] (History's
 * oldest row, any game, or a game tab's archive start when that is older) and [today].
 * [solvedByDay] holds each day's numbered solves as brands, [gamesPerDay] ring segments a day —
 * every game on All, one on a game's tab — and [daysWithRows] the days the tab has any
 * row (solved or not). [archiveRange] is a game tab's openable days (null on All or a
 * game without an archive). [selectedDay] filters the list to one day and survives tab
 * switches; on a game tab, [pastDay] is that day's number and status for the day header
 * (null on All or before it loads). [today] is the puzzle day the list's "Today" /
 * "Yesterday" count from.
 */
data class HistoryUiState(
    val tabs: List<HistoryTab> = emptyList(),
    val rows: List<HistoryDayRow> = emptyList(),
    val stats: GameStats? = null,
    val calendarDay: LocalDate? = null,
    val calendarExpanded: Boolean = false,
    val earliestDay: LocalDate? = null,
    val selectedDay: LocalDate? = null,
    val solvedByDay: Map<LocalDate, List<GameBrand>> = emptyMap(),
    val daysWithRows: Set<LocalDate> = emptySet(),
    val archiveRange: ClosedRange<LocalDate>? = null,
    val pastDay: PastDay? = null,
    val gamesPerDay: Int = 0,
    val today: LocalDate? = null,
) {
    /**
     * Whether the calendar's [date] taps: never after today; on All only a day with rows,
     * on a game's tab also any day of its [archiveRange] (ticket 09).
     */
    fun canSelect(date: LocalDate): Boolean =
        today != null && date <= today && (date in daysWithRows || archiveRange?.contains(date) == true)
}

/**
 * History's state holder: cache-only reads over the GAME-AGNOSTIC merged history
 * ([ObserveGameHistory] — every game's completion records UNION every registered game's
 * captured days; fix round 5, defect C). Rows render per GameId, named by :app's
 * [GameRegistry] and branded by [GamePlayRegistry] (the same lookups Home uses). The
 * list pages lazily from the same merged emissions the state is built from — a paging
 * generation is never built from a pre-emission empty list.
 *
 * Tabs (spec A18): [selectedTab] is null for All or one registered game; the [key]
 * opens on its game, and an unknown id opens All. The stats are [ObserveGameStats] for
 * the tab, the same numbers Profile and Result show (practice never counts), and each
 * game's best row is its fastest numbered solve — the row those stats' best time reads.
 *
 * Calendar: [showCalendar] picks the shown week or month and the mode (clamped in the
 * state to the days with history — on a game tab also its archive range — never past
 * today) and [selectDay] toggles a one-day filter. Both live here beside the tab, so a
 * rotation keeps the calendar, the filter and the rows they are clamped to together.
 *
 * Day header (ticket 09, spec A20): on a game tab the selected day's number and status
 * come from [ObservePastDay] — local data only, so showing it never fetches.
 *
 * Replay is navigation only (ticket 11): the route reports the (game, day) and the play
 * store opens that day's slot — History never builds a session blob.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    key: HistoryKey,
    observeGameHistory: ObserveGameHistory,
    observeGameStats: ObserveGameStats,
    private val registry: GameRegistry,
    private val playRegistry: GamePlayRegistry,
    private val puzzleDay: GetPuzzleDay,
    observePastDay: ObservePastDay,
    private val daily: DailyRepository,
) : ViewModel() {

    private val tabs: List<HistoryTab> = registry.all.map { plugin ->
        val id = plugin.definition.id
        HistoryTab(gameId = id, name = registry.displayName(id), brand = playRegistry.brandOf(id))
    }

    private val tab = MutableStateFlow(key.gameId?.let(::GameId)?.takeIf { registry[it] != null })
    private val calendar = MutableStateFlow(CalendarView(puzzleDay(), expanded = false))
    private val day = MutableStateFlow<LocalDate?>(null)

    /** The selected tab: null = All, otherwise one registered game. */
    val selectedTab: StateFlow<GameId?> = tab.asStateFlow()

    private val allRows: Flow<List<HistoryDayRow>> = observeGameHistory().map { merged ->
        // An archive day from another year spells its year out (A23).
        val currentYear = puzzleDay().year
        merged.map { it.toRow(registry.displayName(it.gameId), playRegistry.brandOf(it.gameId), currentYear) }
            .sortedWith(HISTORY_ROW_ORDER)
            .markBest()
    }

    // The tab with its rows, so the state never pairs a new tab with the old one's rows.
    private val tabRows: Flow<TabRows> = combine(allRows, tab) { rows, game ->
        TabRows(
            tab = game,
            rows = if (game == null) rows else rows.filter { it.gameId == game },
            earliest = rows.minOfOrNull { it.day },
        )
    }.distinctUntilChanged().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // The game tab's selected day (null on All or without a day), as ObservePastDay reads it.
    private val pastDay: Flow<PastDay?> = combine(tab, day) { game, selected -> game to selected }
        .flatMapLatest { (game, selected) ->
            if (game == null || selected == null) flowOf(null) else observePastDay(game, selected)
        }

    val uiState: StateFlow<HistoryUiState> = combine(
        tabRows,
        tab.flatMapLatest { observeGameStats(it) },
        calendar,
        day,
        pastDay,
    ) { (game, rows, oldest), stats, (wanted, wide), selected, past ->
        val order = if (game == null) tabs.map { it.gameId } else listOf(game)
        val today = puzzleDay()
        val range = game?.let(daily::archiveRange)
        val earliest = listOfNotNull(oldest, range?.start).minOrNull()?.coerceAtMost(today) ?: today
        HistoryUiState(
            tabs = tabs,
            rows = rows,
            stats = stats,
            calendarDay = wanted.coerceIn(earliest, today),
            calendarExpanded = wide,
            earliestDay = earliest,
            selectedDay = selected,
            solvedByDay = solvedByDay(rows, order),
            daysWithRows = rows.mapTo(HashSet()) { it.day },
            archiveRange = range,
            gamesPerDay = order.size,
            today = today,
        ).let { state ->
            // Only this tab's own selectable day heads the list, never a stale tab's or day's.
            val current = past?.takeIf { it.game == game && it.day == selected && state.canSelect(it.day) }
            state.copy(pastDay = current)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(tabs = tabs))

    // Rows only: a stats-only emission must not start a new paging generation.
    val pagedDays: Flow<PagingData<HistoryDayRow>> = tabRows.map { it.rows }.distinctUntilChanged()
        .flatMapLatest { historyPager(it) }.cachedIn(viewModelScope)

    /** Switches to [game]'s tab (null = All); an unregistered id is ignored. The day filter stays. */
    fun select(game: GameId?) {
        if (game == null || registry[game] != null) tab.value = game
    }

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

    /** One tab's rows, plus the oldest day across every game (the calendar's first page). */
    private data class TabRows(val tab: GameId?, val rows: List<HistoryDayRow>, val earliest: LocalDate?)

    /** The calendar's wanted day (its week or month is shown) and mode, set together. */
    private data class CalendarView(val day: LocalDate, val expanded: Boolean)
}
