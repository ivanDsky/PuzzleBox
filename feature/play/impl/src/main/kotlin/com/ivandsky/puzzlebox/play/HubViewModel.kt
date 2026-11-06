package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.cachedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.playTarget
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
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
 * One hub card: a playable game, its display name, how its bests rank ([rankOrder]), its
 * [brand], today's puzzle number (null when the day has none or isn't cached yet), today's
 * status (null = not yet known), its [best] daily (null when never solved), its Up-next
 * [line] (null when solved, unknown or the game has none) and the days it can open by date
 * ([archive], null = no archive, so no "Past puzzles").
 */
internal data class HubRow(
    val game: GameId,
    val name: String,
    val rankOrder: RankOrder,
    val brand: GameBrand = GameBrand.Neutral,
    val puzzleNumber: Int? = null,
    val status: TodayStatus? = null,
    val best: BestScore? = null,
    val line: String? = null,
    val archive: ClosedRange<LocalDate>? = null,
)

/**
 * The hub's screen state (A13): the [rows] in registry order, how many of today's puzzles
 * are solved ([doneCount]), the [next] game — the one card drawn as up next (null when
 * every game is solved or no status is known yet) — the sections they make ([plan]) and
 * each game's board thumbnail model ([previews], null or absent while loading, offline or
 * unreadable: the card shows the game's glyph instead).
 */
internal data class HubUiState(
    val rows: List<HubRow>,
    val doneCount: Int = 0,
    val next: GameId? = null,
    val plan: HubPlan = planHub(rows, next),
    val previews: Map<GameId, RendererModel?> = emptyMap(),
)

/**
 * The games hub's state holder: the [PlaySessionStore]'s playable games in registry order,
 * each with its [ObserveTodaySet] status, today's puzzle number (from [ObserveGameHistory]
 * on the game's [GameCalendar] day), its best daily under its own [RankOrder]
 * ([ObserveGameStats], dailies only; the order is the game's [GameRegistry] score model)
 * (A10, A13, A14, SC11), plus the days it can open by date ([DailyRepository.archiveRange],
 * A22: the source's arithmetic, re-read on every status change so the range's end moves
 * with the day). The next game is the play target ([playTarget]) among the games that
 * can load, unless it is already solved; [planHub] derives the sections from both. Before
 * the first emission no row has a status.
 *
 * Each unsolved card's [HubRow.line] is its game's own
 * [com.ivandsky.puzzlebox.gameui.GamePlayUi.hubLine] of today's board, and each card's
 * thumbnail its [com.ivandsky.puzzlebox.gameui.GamePlayUi.previewModel]: an unfinished
 * play's newest session ([PlaySessionStore.latestSave], so it shows the last move even
 * while that save is still on its way to disk), the cached daily fresh when not started or
 * drawn solved when solved ([DailyRepository.cached], never the network); none when
 * offline. Both are built on [previewDispatcher], again on every status change; a card
 * keeps its last line and thumbnail until the new ones are ready.
 *
 * The sources are collected only while the hub is observed (plus a 5 s grace), so a hub
 * left in a back stack across a deep sleep re-reads the day's cues and boards when it
 * shows again. Each time the hub starts, [syncToday] prefetches today's uncached puzzles
 * ([SyncTodayPuzzles], A12), so a game that can't load reads Offline. Building the
 * ViewModel fetches nothing. It never names a game.
 */
class HubViewModel(
    private val store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    private val syncTodayPuzzles: SyncTodayPuzzles,
    stats: ObserveGameStats,
    history: ObserveGameHistory,
    private val calendar: GameCalendar,
    games: GameRegistry,
    private val registry: GamePlayRegistry,
    private val daily: DailyRepository,
    private val previewDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val seed = store.playable.map { (game, name) ->
        HubRow(
            game = game,
            name = name,
            rankOrder = games[game]?.score?.rankOrder ?: RankOrder.TimeThenHints,
            brand = registry.brandOf(game),
            archive = daily.archiveRange(game),
        )
    }

    private val bests: Flow<Map<GameId, BestScore?>> =
        if (seed.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(seed.map { row -> stats(row.game).map { row.game to it.best } }) { it.toMap() }
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

    /** One card's reads of today's board: its thumbnail model and its Up-next line. */
    private data class Board(val preview: RendererModel?, val line: String?)

    /** The last boards read; read and written on the collector's (main) thread only. */
    private var shownBoards: Map<GameId, Board> = emptyMap()

    /** Each status set at once with the boards already shown, then with its own once read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val today: Flow<Pair<List<TodayEntry>, Map<GameId, Board>>> =
        todaySet().transformLatest { entries ->
            emit(entries to shownBoards)
            shownBoards = boardsOf(entries)
            emit(entries to shownBoards)
        }

    internal val uiState: StateFlow<HubUiState> =
        combine(today, numbers, bests.onStart { emit(emptyMap()) }) { (entries, boards), number, best ->
            val status = entries.associate { it.game to it.status }
            HubUiState(
                rows = seed.map {
                    it.copy(
                        puzzleNumber = number[it.game],
                        status = status[it.game],
                        best = best[it.game],
                        line = boards[it.game]?.line,
                        archive = daily.archiveRange(it.game),
                    )
                },
                doneCount = seed.count { status[it.game] is TodayStatus.Solved },
                next = nextOf(entries.filter { entry -> seed.any { it.game == entry.game } }),
                previews = boards.mapValues { it.value.preview },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HubUiState(seed))

    /** Fetches today's uncached puzzles; a failure shows as that game's Offline card. */
    internal fun syncToday() {
        viewModelScope.launch { syncTodayPuzzles() }
    }

    private fun nextOf(entries: List<TodayEntry>): GameId? {
        val loadable = entries.filter { it.status != TodayStatus.Offline }
        val target = loadable.playTarget() ?: return null
        return target.takeIf { game -> loadable.first { it.game == game }.status !is TodayStatus.Solved }
    }

    private suspend fun boardsOf(entries: List<TodayEntry>): Map<GameId, Board> {
        val status = entries.associate { it.game to it.status }
        return seed.associate { row -> row.game to boardOf(row.game, status[row.game]) }
    }

    /** [game]'s reads of today's [status]: nothing to read offline or unknown, no line once solved. */
    private suspend fun boardOf(game: GameId, status: TodayStatus?): Board {
        val none = Board(preview = null, line = null)
        val day = calendar.today(game) ?: return none
        val board = when (status) {
            is TodayStatus.InProgress -> store.latestSave(PlaySlot(game, day))?.let(BoardPreview::InProgress)
            TodayStatus.NotStarted -> daily.cachedPuzzle(game, day)?.let(BoardPreview::Fresh)
            is TodayStatus.Solved -> daily.cachedPuzzle(game, day)?.let(BoardPreview::Solved)
            TodayStatus.Offline, null -> null
        } ?: return none
        val ui = registry[game] ?: return none
        return withContext(previewDispatcher) {
            Board(preview = ui.previewModel(board), line = if (status is TodayStatus.Solved) null else ui.hubLine(board))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
