package com.ivandsky.puzzlebox.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * Everything History renders besides the paged list, for the selected tab: the game
 * [tabs], the tab's full [rows] (selection, best and day-filter lookups must see every
 * day, not just the loaded pages), and its [stats] (null until the first emission).
 *
 * The calendar shows [month] (null until the first emission), between [earliestMonth]
 * (the month of History's oldest row, any game — stable across tabs) and [today]'s.
 * [solvedByDay] holds each day's numbered solves as brands, [dotsPerDay] dots a day —
 * every game on All, one on a game's tab — and [daysWithRows] the days the tab has any
 * row (solved or not). [selectedDay] filters the list to one day and survives tab
 * switches. [today] is the puzzle day the list's "Today" / "Yesterday" count from.
 */
data class HistoryUiState(
    val tabs: List<HistoryTab> = emptyList(),
    val rows: List<HistoryDayRow> = emptyList(),
    val stats: GameStats? = null,
    val month: YearMonth? = null,
    val earliestMonth: YearMonth? = null,
    val selectedDay: LocalDate? = null,
    val solvedByDay: Map<LocalDate, List<GameBrand>> = emptyMap(),
    val daysWithRows: Set<LocalDate> = emptySet(),
    val dotsPerDay: Int = 0,
    val today: LocalDate? = null,
)

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
 * Calendar: [showMonth] picks the month (clamped in the state to the months with
 * history, never past the current one) and [selectDay] toggles a one-day filter.
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
) : ViewModel() {

    private val tabs: List<HistoryTab> = registry.all.map { plugin ->
        val id = plugin.definition.id
        HistoryTab(gameId = id, name = registry.displayName(id), brand = playRegistry.brandOf(id))
    }

    private val tab = MutableStateFlow(key.gameId?.let(::GameId)?.takeIf { registry[it] != null })
    private val month = MutableStateFlow(YearMonth.from(puzzleDay()))
    private val day = MutableStateFlow<LocalDate?>(null)

    /** The selected tab: null = All, otherwise one registered game. */
    val selectedTab: StateFlow<GameId?> = tab.asStateFlow()

    private val allRows: Flow<List<HistoryDayRow>> = observeGameHistory().map { merged ->
        merged.map { it.toRow(registry.displayName(it.gameId), playRegistry.brandOf(it.gameId)) }
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

    val uiState: StateFlow<HistoryUiState> = combine(
        tabRows,
        tab.flatMapLatest { observeGameStats(it) },
        month,
        day,
    ) { (game, rows, earliestDay), stats, wanted, selected ->
        val order = if (game == null) tabs.map { it.gameId } else listOf(game)
        val today = puzzleDay()
        val current = YearMonth.from(today)
        val earliest = earliestDay?.let(YearMonth::from)?.coerceAtMost(current) ?: current
        HistoryUiState(
            tabs = tabs,
            rows = rows,
            stats = stats,
            month = wanted.coerceIn(earliest, current),
            earliestMonth = earliest,
            selectedDay = selected,
            solvedByDay = solvedByDay(rows, order),
            daysWithRows = rows.mapTo(HashSet()) { it.day },
            dotsPerDay = order.size,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(tabs = tabs))

    // Rows only: a stats-only emission must not start a new paging generation.
    val pagedDays: Flow<PagingData<HistoryDayRow>> = tabRows.map { it.rows }.distinctUntilChanged()
        .flatMapLatest { historyPager(it) }.cachedIn(viewModelScope)

    /** Switches to [game]'s tab (null = All); an unregistered id is ignored. The day filter stays. */
    fun select(game: GameId?) {
        if (game == null || registry[game] != null) tab.value = game
    }

    /** Shows [month]'s calendar; the state clamps it between the oldest row's month and today's. */
    fun showMonth(month: YearMonth) {
        this.month.value = month
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

    /** One tab's rows, plus the oldest day across every game (the calendar's first month). */
    private data class TabRows(val tab: GameId?, val rows: List<HistoryDayRow>, val earliest: LocalDate?)
}
