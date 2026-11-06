package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObservePastDay
import com.ivandsky.puzzlebox.domain.PastDay
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.playTarget
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One hub card: a playable game, its display name, its [brand], today's puzzle number
 * (null when the day has none or isn't cached yet), today's status (null = not yet known)
 * and the days it can open by date ([archive], null = no archive, so no "Past puzzles").
 */
internal data class HubRow(
    val game: GameId,
    val name: String,
    val brand: GameBrand = GameBrand.Neutral,
    val puzzleNumber: Int? = null,
    val status: TodayStatus? = null,
    val archive: ClosedRange<LocalDate>? = null,
)

/**
 * The hub's screen state (A13): the [rows] in registry order, each game's best daily time
 * ([bestByGame], null when never solved), how many of today's puzzles are solved
 * ([doneCount]), the [next] game — the one card drawn as up next (null when every game
 * is solved or no status is known yet) — and each game's board thumbnail model
 * ([previews], null or absent while loading, offline or unreadable: the card shows the
 * game's glyph instead).
 */
internal data class HubUiState(
    val rows: List<HubRow>,
    val bestByGame: Map<GameId, Long?> = emptyMap(),
    val doneCount: Int = 0,
    val next: GameId? = null,
    val previews: Map<GameId, RendererModel?> = emptyMap(),
)

/**
 * The games hub's state holder: the [PlaySessionStore]'s playable games in registry order,
 * each with its [ObserveTodaySet] status, today's puzzle number (from [ObserveGameHistory]
 * on the game's [GameCalendar] day) and its best time ([ObserveGameStats], dailies only)
 * (A13, A14, SC11), plus the days it can open by date ([DailyRepository.archiveRange],
 * A22: the source's arithmetic, re-read on every status change so the range's end moves
 * with the day). The next game is the play target ([playTarget]) among the games that
 * can load, unless it is already solved. Before the first emission no row has a status.
 *
 * Each card's thumbnail is its game's own [com.ivandsky.puzzlebox.gameui.GamePlayUi.previewModel]
 * of today: an unfinished play's newest session ([PlaySessionStore.latestSave], so it
 * shows the last move even while that save is still on its way to disk), the cached daily
 * fresh when not started or drawn solved when solved ([DailyRepository.cached], never the
 * network); none when offline. The models are built on [previewDispatcher], again on every
 * status change; a card keeps its last thumbnail until the new one is ready.
 *
 * The sources are collected only while the hub is observed (plus a 5 s grace), so a hub
 * left in a back stack across a deep sleep re-reads the day's cues and boards when it
 * shows again. Each time the hub starts, [syncToday] prefetches today's uncached puzzles
 * ([SyncTodayPuzzles], A12), so a game that can't load reads Offline. Building the
 * ViewModel fetches nothing. It never names a game.
 *
 * The Past puzzles picker's headline reads its selected day through [pastDay]
 * ([ObservePastDay]: local data only, so moving the selection fetches nothing).
 */
class HubViewModel(
    private val store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    private val syncTodayPuzzles: SyncTodayPuzzles,
    stats: ObserveGameStats,
    history: ObserveGameHistory,
    private val calendar: GameCalendar,
    private val registry: GamePlayRegistry,
    private val daily: DailyRepository,
    private val observePastDay: ObservePastDay,
    private val previewDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val seed = store.playable.map { (game, name) ->
        HubRow(game, name, registry.brandOf(game), archive = daily.archiveRange(game))
    }

    private val bests: Flow<Map<GameId, Long?>> =
        if (seed.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(seed.map { row -> stats(row.game).map { row.game to it.bestTimeMs } }) { it.toMap() }
        }

    // Seeded empty, like the bests, so the cards never wait on history to show today's set.
    private val numbers: Flow<Map<GameId, Int>> = history()
        .map { days ->
            seed.mapNotNull { row ->
                val today = calendar.today(row.game)
                days.firstOrNull { it.gameId == row.game && it.day == today }?.puzzleNumber?.let { row.game to it }
            }.toMap()
        }
        .onStart { emit(emptyMap()) }

    /** The last thumbnails built; read and written on the collector's (main) thread only. */
    private var shownPreviews: Map<GameId, RendererModel?> = emptyMap()

    /** Each status set at once with the thumbnails already shown, then with its own once built. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val today: Flow<Pair<List<TodayEntry>, Map<GameId, RendererModel?>>> =
        todaySet().transformLatest { entries ->
            emit(entries to shownPreviews)
            shownPreviews = previewsOf(entries)
            emit(entries to shownPreviews)
        }

    internal val uiState: StateFlow<HubUiState> =
        combine(today, numbers, bests.onStart { emit(emptyMap()) }) { (entries, previews), number, best ->
            val status = entries.associate { it.game to it.status }
            HubUiState(
                rows = seed.map {
                    it.copy(
                        puzzleNumber = number[it.game],
                        status = status[it.game],
                        archive = daily.archiveRange(it.game),
                    )
                },
                bestByGame = best,
                doneCount = seed.count { status[it.game] is TodayStatus.Solved },
                next = nextOf(entries.filter { entry -> seed.any { it.game == entry.game } }),
                previews = previews,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HubUiState(seed))

    /** Fetches today's uncached puzzles; a failure shows as that game's Offline card. */
    internal fun syncToday() {
        viewModelScope.launch { syncTodayPuzzles() }
    }

    /** [game]'s [day] number and status, for the Past puzzles picker's headline (A20, A22). */
    internal fun pastDay(game: GameId, day: LocalDate): Flow<PastDay> = observePastDay(game, day)

    private fun nextOf(entries: List<TodayEntry>): GameId? {
        val loadable = entries.filter { it.status != TodayStatus.Offline }
        val target = loadable.playTarget() ?: return null
        return target.takeIf { game -> loadable.first { it.game == game }.status !is TodayStatus.Solved }
    }

    private suspend fun previewsOf(entries: List<TodayEntry>): Map<GameId, RendererModel?> {
        val status = entries.associate { it.game to it.status }
        return seed.associate { row -> row.game to previewOf(row.game, status[row.game]) }
    }

    /** [game]'s thumbnail for today's [status]; null when there is nothing to draw or the game can't read it. */
    private suspend fun previewOf(game: GameId, status: TodayStatus?): RendererModel? {
        val day = calendar.today(game) ?: return null
        val preview = when (status) {
            is TodayStatus.InProgress -> store.latestSave(PlaySlot(game, day))?.let(BoardPreview::InProgress)
            TodayStatus.NotStarted -> daily.cachedPuzzle(game, day)?.let(BoardPreview::Fresh)
            is TodayStatus.Solved -> daily.cachedPuzzle(game, day)?.let(BoardPreview::Solved)
            TodayStatus.Offline, null -> null
        } ?: return null
        val ui = registry[game] ?: return null
        return withContext(previewDispatcher) { ui.previewModel(preview) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
