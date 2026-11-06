package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The library's screen state (A25): every playable game as a [rows] entry in registry
 * order, the [selected] one (the tablet's open page; the first game until a tap), and
 * whether any game reads Offline ([offline]), which shows the offline pill.
 */
internal data class LibraryUiState(
    val rows: List<LibraryRowModel>,
    val selected: GameId,
    val offline: Boolean,
)

/**
 * One game in the library: its [card] (status null = still loading; puzzle number =
 * [today]), today's puzzle number, its archive [progress] (null for a game without an
 * archive, or before it loads), its [stats] and its three how-to-play [rules].
 */
internal data class LibraryRowModel(
    val card: GameCardModel,
    val today: Int?,
    val progress: ArchiveProgress?,
    val stats: GameStats,
    val rules: List<String>,
)

/** An open lesson: [game]'s tutorial running in [session]. */
internal data class OpenLesson(val game: GameId, val session: TutorialSession)

/**
 * The games library's state holder (A25–A29): the [PlaySessionStore]'s playable games,
 * each with its [ObserveTodaySet] status, today's puzzle number, its [ObserveArchiveProgress]
 * and its [ObserveGameStats]. Today's number is the captured history's, else the source's
 * ([DailyRepository.puzzleNumber]), else the archive's count, which ends on today.
 *
 * The sources are collected only while the library is observed (plus a 5 s grace), so a
 * library left in a back stack across a deep sleep re-reads the day when it shows again.
 * Each start prefetches today's uncached puzzles ([syncToday], A12), so a game that can't
 * load reads Offline. The lesson lives here, so a rotation keeps its step.
 */
class LibraryViewModel(
    store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    private val syncTodayPuzzles: SyncTodayPuzzles,
    stats: ObserveGameStats,
    history: ObserveGameHistory,
    progress: ObserveArchiveProgress,
    private val calendar: GameCalendar,
    games: GameRegistry,
    private val registry: GamePlayRegistry,
    private val daily: DailyRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val seed: List<LibraryRowModel> = store.playable.map { (game, name) ->
        val order = games[game]?.score?.rankOrder ?: RankOrder.TimeThenHints
        LibraryRowModel(
            card = GameCardModel(game = game, name = name, brand = registry.brandOf(game), status = null, rankOrder = order),
            today = null,
            progress = null,
            stats = GameStats(0, 0, 0, 0, null, null, null, order),
            rules = gameRules(game),
        )
    }

    private val selected = MutableStateFlow(seed.firstOrNull()?.card?.game ?: GameId(""))

    private val _lesson = MutableStateFlow<OpenLesson?>(null)
    internal val lesson: StateFlow<OpenLesson?> = _lesson.asStateFlow()

    private val allStats: Flow<Map<GameId, GameStats>> = perGame { stats(it) }

    private val allProgress: Flow<Map<GameId, ArchiveProgress?>> = perGame { progress(it) }

    // Seeded empty, like stats and progress, so rows never wait on history to show today.
    private val numbers: Flow<Map<GameId, Int>> = history()
        .map { days ->
            seed.mapNotNull { row ->
                val game = row.card.game
                val today = calendar.today(game)
                days.firstOrNull { it.gameId == game && it.day == today }?.puzzleNumber?.let { game to it }
            }.toMap()
        }
        .onStart { emit(emptyMap()) }

    internal val uiState: StateFlow<LibraryUiState> =
        combine(
            todaySet(),
            numbers,
            allStats.onStart { emit(emptyMap()) },
            allProgress.onStart { emit(emptyMap()) },
            selected,
        ) { entries, number, stat, prog, pick ->
            val status = entries.associate { it.game to it.status }
            val rows = seed.map { row ->
                val game = row.card.game
                val archive = prog[game]
                val today = number[game] ?: todayNumber(game, archive)
                row.copy(
                    card = row.card.copy(status = status[game], puzzleNumber = today, best = stat[game]?.best),
                    today = today,
                    progress = archive,
                    stats = stat[game] ?: row.stats,
                )
            }
            LibraryUiState(rows, pick, offline = rows.any { it.card.status == TodayStatus.Offline })
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            LibraryUiState(seed, selected.value, offline = false),
        )

    /** Fetches today's uncached puzzles; a failure shows as that game's Offline row. */
    internal fun syncToday() {
        viewModelScope.launch { syncTodayPuzzles() }
    }

    /** [game]'s page opens (the tablet's right pane; the phone pushes it). */
    internal fun select(game: GameId) {
        selected.value = game
    }

    /** [game]'s lesson opens over the library; a no-op without lessons or while one is open. */
    internal fun openLesson(game: GameId) {
        val steps = registry[game]?.tutorial.orEmpty()
        if (steps.isEmpty() || _lesson.value != null) return
        _lesson.value = OpenLesson(game, TutorialSession(steps))
    }

    /** The lesson ended (finished, skipped or backed out): its game is marked seen. */
    internal fun closeLesson() {
        val open = _lesson.value ?: return
        _lesson.value = null
        viewModelScope.launch { prefs.markTutorialSeen(open.game) }
    }

    private fun todayNumber(game: GameId, archive: ArchiveProgress?): Int? =
        calendar.today(game)?.let { daily.puzzleNumber(game, it) } ?: archive?.total

    private fun <T> perGame(source: (GameId) -> Flow<T>): Flow<Map<GameId, T>> =
        if (seed.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(seed.map { row -> source(row.card.game).map { row.card.game to it } }) { it.toMap() }
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
