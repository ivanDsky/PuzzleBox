package com.ivandsky.puzzlebox.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
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
import com.ivandsky.puzzlebox.domain.heldWhileStreak
import com.ivandsky.puzzlebox.domain.playTarget
import com.ivandsky.puzzlebox.domain.resettingOnAccountChange
import com.ivandsky.puzzlebox.domain.seededOnce
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.FirstScreenReady
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
 * resume) asks [GetAppUpdate] for a newer release; its URL lands in [HomeUiState.updateUrl]. The picked
 * day's solved games each carry the player's place that day ([ObserveResultRank], signed in only).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    observeGameHistory: ObserveGameHistory,
    observeGameStats: ObserveGameStats,
    todaySet: ObserveTodaySet,
    private val identityService: IdentityService,
    private val registry: GameRegistry,
    private val brands: GamePlayRegistry,
    private val puzzleDay: GetPuzzleDay,
    private val nextRollover: GetNextRollover,
    private val syncTodayPuzzles: SyncTodayPuzzles,
    private val getAppUpdate: GetAppUpdate,
    observeLatestSolvedToday: ObserveLatestSolvedToday,
    private val observeResultRank: ObserveResultRank,
    private val idTokens: GoogleIdTokenProvider,
    private val savedState: SavedStateHandle,
    private val firstScreenReady: FirstScreenReady,
) : ViewModel() {

    // The held values reset when this changes: a sign-in as someone else never shows the first account's.
    private fun account(): String? = identityService.current.value?.uid

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
        }.seededOnce(emptyMap(), ::account)

    // Before the set lands every row is unknown and the CTA opens the first game.
    private val seedTarget = seed.firstOrNull()?.game

    // Seeded (null) so the recent days never wait on today's set (it reads Room and DataStore).
    private val today: Flow<List<TodayEntry>?> = todaySet().seededOnce<List<TodayEntry>?>(null, ::account)

    // Seeded (null) like today, so first load never waits on the slowest local read.
    private val history: Flow<List<GameHistoryDay>?> = observeGameHistory().seededOnce<List<GameHistoryDay>?>(null, ::account)
    private val stats: Flow<GameStats?> = observeGameStats().seededOnce<GameStats?>(null, ::account)

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
    }.distinctUntilChanged().seededOnce(null, ::account)

    // The picked day's solved games and the player's place on each; empty while signed out or with no pick.
    private val standings: Flow<Map<GameId, HistoryDay.Standing>> =
        combine(pickedDay, history, identityService.current) { picked, held, identity ->
            val day = picked?.let(LocalDate::parse)
            val solved = if (day == null || identity == null) {
                emptyList()
            } else {
                held.orEmpty().filter { it.day == day && it.record != null }.map { it.gameId }.sortedBy { it.value }
            }
            day to solved
        }.distinctUntilChanged().flatMapLatest { (day, solved) ->
            if (day == null || solved.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(solved.map { game -> observeResultRank(game, day).map { game to (it as? ResultRank.Ranked)?.standing() } }) { places ->
                    places.mapNotNull { (game, standing) -> standing?.let { game to it } }.toMap()
                }
            }
        }

    internal val uiState: StateFlow<HomeUiState> =
        combine(
            today,
            history,
            stats,
            identityService.current,
            bests,
        ) { entries, held, heldStats, identity, best ->
            val days = held.orEmpty()
            val stats = heldStats ?: NO_STATS
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
                week = homeWeek(days, endingOn = day, stats.freezes?.frozenDays.orEmpty(), registryOrder, brands),
                rolloverAt = nextRollover(),
                freezesHeld = stats.freezes?.heldWhileStreak(stats.currentStreak),
                isLocalStateLoaded = entries != null && held != null && heldStats != null,
            )
        }.combine(updateUrl) { state, url -> state.copy(updateUrl = url) }
            .combine(rank) { state, rank -> state.copy(rank = rank) }
            .combine(pickedDay) { state, picked ->
                val listed = picked?.takeIf { day -> state.history.any { it.isoDay == day } }
                if (picked != null && listed == null && state.isLocalStateLoaded) savedState[SELECTED_DAY_KEY] = null
                state.copy(selectedDay = listed)
            }
            .combine(standings) { state, places ->
                // Only the open day carries places, so the other days keep their rows as history built them.
                state.copy(history = state.history.map { if (it.isoDay == state.selectedDay) it.withStandings(places) else it })
            }
            .onEach { if (it.isLocalStateLoaded) firstScreenReady.markReady() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000).resettingOnAccountChange(identityService.current.map { it?.uid }),
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
                        isLocalStateLoaded = false,
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

    /** Opens the Google account picker and signs in with its token; a cancel or failure leaves the player signed out. */
    suspend fun signIn(activityContext: Context) {
        val token = idTokens.idToken(activityContext) as? GoogleIdToken.Available ?: return
        identityService.signInWithGoogleIdToken(token.token)
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
                    games = gameResults(games.associateBy { it.gameId }),
                )
            }

    // Every registered game, in registry order, then any unregistered game that has a row; a game with no row was not played.
    private fun gameResults(played: Map<GameId, GameHistoryDay>): List<HistoryDay.GameResult> {
        val ids = registry.all.map { it.definition.id } + played.keys.filter { registryRank(it) == Int.MAX_VALUE }.sortedBy { it.value }
        return ids.map { id ->
            val record = played[id]?.record
            HistoryDay.GameResult(
                gameId = id,
                gameName = registry.displayName(id),
                brand = brands.brandOf(id),
                outcome = when {
                    record != null -> HistoryDay.Outcome.Solved(record.time, record.hints)
                    played.containsKey(id) -> HistoryDay.Outcome.Opened
                    else -> HistoryDay.Outcome.NotPlayed
                },
            )
        }
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
