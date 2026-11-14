package com.ivandsky.puzzlebox.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetAppUpdate
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLatestSolvedToday
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.frozenDaysOrEmpty
import com.ivandsky.puzzlebox.domain.heldWhileStreak
import com.ivandsky.puzzlebox.domain.playTarget
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
import kotlinx.coroutines.launch

/**
 * Everything Home renders: today's set, the signed-in player, the recent days, and
 * [puzzleDay] — the puzzle roll-over day (A9) the header shows, not the phone's date.
 * [playTarget] is the game the one CTA opens (core:domain's `playTarget`: the first game
 * with an unfinished save, else the first unsolved one, else the first game; before the
 * set lands, the first game). [stats] and [week] feed the streak card (A8): the solved-day
 * numbers and the last 7 puzzle days, oldest first, ending on [puzzleDay]. [freezesHeld] is the count
 * the streak card shows, already ruled by [heldWhileStreak] (null hides it). [rolloverAt] is
 * when the next puzzles land, the CTA's countdown once every game is solved. [updateUrl] is
 * a newer release's page (spec A7), null while none is known. [rank] is the player's place on
 * the game they solved last today, null unless signed in and ranked. [selectedDay]: two-pane's detail day.
 */
internal data class HomeUiState(
    val today: List<TodayRow>,
    val identity: Identity?,
    val history: List<HistoryDay>,
    val puzzleDay: LocalDate,
    val playTarget: GameId?,
    val stats: GameStats,
    val week: List<WeekDay>,
    val rolloverAt: Instant,
    val freezesHeld: Int? = null,
    val updateUrl: String? = null,
    val rank: TodayRank? = null,
    val selectedDay: String? = null,
) {
    /**
     * One game's Today row (A9, A10): [status] is null until today's set first emits,
     * [best] until the game's first daily solve; [best] ranks by the game's own [rankOrder].
     */
    data class TodayRow(
        val game: GameId,
        val displayName: String,
        val status: TodayStatus?,
        val brand: GameBrand,
        val rankOrder: RankOrder,
        val best: BestScore? = null,
    )

    /** The row the CTA names; null only for an empty set. */
    val target: TodayRow?
        get() = today.firstOrNull { it.game == playTarget }

    /** Every game of today's set is solved: the CTA turns into the countdown. */
    val allSolved: Boolean
        get() = today.isNotEmpty() && today.all { it.status is TodayStatus.Solved }
}

/** [SavedStateHandle] key of the picked day's iso date, so the two-pane detail survives process death. */
internal const val SELECTED_DAY_KEY = "home.selectedDay"

/**
 * Home's state holder. The Today tiles are [ObserveTodaySet] (A10): every registered game in
 * registry order with its status, seeded from [registry] so the first frame already lists
 * every game. Each game's brand comes from [brands] ([GamePlayRegistry.brandOf], A2). The
 * recent days observe the GAME-AGNOSTIC merged history ([ObserveGameHistory] — every game's
 * completion records UNION every registered game's captured days; fix round 5, defect C)
 * grouped by day: the newest [RECENT_DAYS] days before the puzzle day (today lives in the
 * tiles), each listing its games in registry order (unregistered games last) with their
 * solve time, or null when opened but not solved (A11). The same history builds the week:
 * a day's solved dailies (practice plays excluded, as [ObserveGameStats] does). Per-game
 * names resolve against :app's [GameRegistry], so a new game appears here purely by
 * registering its plugin. The header's day and [GetNextRollover]'s instant are re-read on
 * every emission. [syncToday] prefetches today's uncached puzzles (A12; the route calls it
 * on every resume), so a game that can't load reads Offline. [checkForUpdate] (also on every
 * resume) asks [GetAppUpdate] for a newer release; its URL lands in [HomeUiState.updateUrl].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    observeGameHistory: ObserveGameHistory,
    observeGameStats: ObserveGameStats,
    todaySet: ObserveTodaySet,
    identityService: IdentityService,
    private val registry: GameRegistry,
    private val brands: GamePlayRegistry,
    private val puzzleDay: GetPuzzleDay,
    private val nextRollover: GetNextRollover,
    private val syncTodayPuzzles: SyncTodayPuzzles,
    private val getAppUpdate: GetAppUpdate,
    observeLatestSolvedToday: ObserveLatestSolvedToday,
    observeResultRank: ObserveResultRank,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val updateUrl = MutableStateFlow<String?>(null)
    private val pickedDay: StateFlow<String?> = savedState.getStateFlow(SELECTED_DAY_KEY, null)

    /** The recent day the two-pane detail shows; null clears it. A day history drops is forgotten for good. */
    fun selectDay(isoDay: String?) = savedState.set(SELECTED_DAY_KEY, isoDay)

    // Declared before uiState: its initial value already builds the week.
    private val registryOrder: Comparator<GameHistoryDay> =
        compareBy({ registryRank(it.gameId) }, { it.gameId.value })

    private val seed = registry.all.map {
        val id = it.definition.id
        HomeUiState.TodayRow(
            id, it.definition.displayName, status = null, brand = brands.brandOf(id), rankOrder = rankOrderOf(id),
        )
    }

    private val bests: Flow<Map<GameId, BestScore?>> =
        if (seed.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(seed.map { row -> observeGameStats(row.game).map { row.game to it.best } }) { it.toMap() }
        }.onStart { emit(emptyMap()) }

    // Before the set lands every row is unknown and the CTA opens the first game.
    private val seedTarget = seed.firstOrNull()?.game

    // Seeded (null) so the recent days never wait on today's set (it reads Room and DataStore).
    private val today: Flow<List<TodayEntry>?> = todaySet().onStart<List<TodayEntry>?> { emit(null) }

    // Off until Today draws the summary rows, so a Home without them never reads the leaderboard.
    private val summariesShown = MutableStateFlow(false)

    /** Set by the route while Today shows summaries; the rank stream runs only then. */
    fun setSummariesShown(shown: Boolean) {
        summariesShown.value = shown
    }

    // A new latest solve cancels the old game's rank stream; no rank until a ranked result lands.
    private val rank: Flow<TodayRank?> = summariesShown.flatMapLatest { shown ->
        if (!shown) {
            flowOf(null)
        } else {
            observeLatestSolvedToday().flatMapLatest { game ->
                if (game == null) {
                    flowOf(null)
                } else {
                    observeResultRank(game, puzzleDay()).map { result ->
                        (result as? ResultRank.Ranked)?.let { TodayRank(game, registry.displayName(game), it.position) }
                    }
                }
            }
        }
    }.distinctUntilChanged().onStart { emit(null) }

    internal val uiState: StateFlow<HomeUiState> =
        combine(
            today,
            observeGameHistory(),
            observeGameStats(),
            identityService.current,
            bests,
        ) { entries, days, stats, identity, best ->
            val day = puzzleDay()
            val rows = entries?.map {
                HomeUiState.TodayRow(it.game, it.displayName, it.status, brands.brandOf(it.game), rankOrderOf(it.game))
            } ?: seed
            HomeUiState(
                today = rows.map { it.copy(best = best[it.game]) },
                identity = identity,
                history = recentDays(days, before = day),
                puzzleDay = day,
                playTarget = if (entries == null) seedTarget else entries.playTarget(),
                stats = stats,
                week = homeWeek(days, endingOn = day, stats.freezes.frozenDaysOrEmpty, registryOrder, brands),
                rolloverAt = nextRollover(),
                freezesHeld = stats.freezes.heldWhileStreak(stats.currentStreak),
            )
        }.combine(updateUrl) { state, url -> state.copy(updateUrl = url) }
            .combine(rank) { state, rank -> state.copy(rank = rank) }
            .combine(pickedDay) { state, picked ->
                val listed = picked?.takeIf { day -> state.history.any { it.isoDay == day } }
                if (picked != null && listed == null) savedState[SELECTED_DAY_KEY] = null
                state.copy(selectedDay = listed)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = puzzleDay().let { day ->
                    HomeUiState(
                        today = seed,
                        identity = identityService.current.value,
                        history = emptyList(),
                        puzzleDay = day,
                        playTarget = seedTarget,
                        stats = NO_STATS,
                        week = homeWeek(emptyList(), endingOn = day, emptySet(), registryOrder, brands),
                        rolloverAt = nextRollover(),
                    )
                },
            )

    fun syncToday() {
        viewModelScope.launch { syncTodayPuzzles() }
    }

    /** Off the first frame: the pill appears only once a newer release is known (A4). */
    fun checkForUpdate() {
        viewModelScope.launch { updateUrl.value = getAppUpdate()?.url }
    }

    private fun recentDays(days: List<GameHistoryDay>, before: LocalDate): List<HistoryDay> =
        days.asSequence()
            .filter { it.day < before }
            .groupBy { it.day }
            .entries
            .sortedByDescending { it.key }
            .take(RECENT_DAYS)
            .map { (day, games) ->
                HistoryDay(
                    isoDay = day.toString(),
                    headline = day.format(HEADLINE),
                    games = games
                        .sortedWith(registryOrder)
                        .map { game ->
                            HistoryDay.GameResult(
                                gameName = registry.displayName(game.gameId),
                                brand = brands.brandOf(game.gameId),
                                time = game.record?.let { formatDuration(it.time) },
                            )
                        },
                )
            }

    private fun registryRank(id: GameId): Int =
        registry.all.indexOfFirst { it.definition.id == id }.let { if (it < 0) Int.MAX_VALUE else it }

    private fun rankOrderOf(id: GameId): RankOrder = registry[id]?.score?.rankOrder ?: RankOrder.TimeThenHints

    private companion object {
        const val RECENT_DAYS = 3
        val HEADLINE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")
        val NO_STATS = GameStats(
            currentStreak = 0,
            bestStreak = 0,
            solved = 0,
            hintFree = 0,
            averageTimeMs = null,
            playingSince = null,
            best = null,
            rankOrder = RankOrder.TimeThenHints,
        )
    }
}
