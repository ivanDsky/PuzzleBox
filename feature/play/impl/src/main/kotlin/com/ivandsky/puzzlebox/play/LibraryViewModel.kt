package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

/**
 * The selected game's calendar (A26): the shown [month] and its archive [days], and the
 * [picked] past day the day card shows (null = today).
 */
internal data class ArchivePageState(
    val game: GameId,
    val month: YearMonth,
    val days: List<ArchiveDay>,
    val picked: ArchiveDay?,
)

/** An open lesson: [game]'s tutorial running in [session]. */
internal data class OpenLesson(val game: GameId, val session: TutorialSession)

/** A friend's puzzle in From friends: its [row], and its game's name and brand ([GameBrand.Neutral] for a game this build lacks). */
internal data class FriendCardModel(val row: FriendPuzzleRow, val gameName: String, val brand: GameBrand)

/**
 * The games library's state holder (A25–A29): the [PlaySessionStore]'s playable games,
 * each with its [ObserveTodaySet] status, today's puzzle number, its [ObserveArchiveProgress]
 * and its [ObserveGameStats]. Today's number is the captured history's, else the source's
 * ([DailyRepository.puzzleNumber]), else the archive's count, which ends on today.
 *
 * The sources are collected only while the library is observed (plus a 5 s grace), so a
 * library left in a back stack across a deep sleep re-reads the day when it shows again.
 * Each start prefetches today's uncached puzzles ([syncToday], A12), so a game that can't
 * load reads Offline. The lesson lives here, so a rotation keeps its step. [friends] is
 * From friends ([ObserveFriendPuzzles]), each puzzle under its game's name and brand; a
 * [removeFriend] hides the card at once and deletes it when its Undo window closes
 * ([FriendRemoval]), in [appScope] so the delete outlives this screen.
 *
 * The selected game's [page] is its calendar month ([ObserveArchiveMonth], today's until
 * [showMonth]) and the past day [pick]ed for the day card; a month change keeps the picked
 * day, and a game switch or [backToToday] returns both to today.
 */
class LibraryViewModel(
    store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    private val syncTodayPuzzles: SyncTodayPuzzles,
    stats: ObserveGameStats,
    history: ObserveGameHistory,
    progress: ObserveArchiveProgress,
    private val archiveMonth: ObserveArchiveMonth,
    private val calendar: GameCalendar,
    games: GameRegistry,
    private val registry: GamePlayRegistry,
    private val daily: DailyRepository,
    private val prefs: UserPreferencesRepository,
    friendPuzzles: ObserveFriendPuzzles,
    deleteReceivedPuzzle: DeleteReceivedPuzzle,
    appScope: CoroutineScope,
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

    // Null is today's month / today: both follow the game's day without a stored date.
    private val month = MutableStateFlow<YearMonth?>(null)
    private val picked = MutableStateFlow<LocalDate?>(null)

    /**
     * The month the calendar asked for (null = today's), ahead of [page]: its days load from
     * disk, and a ‹ › step must count from the month on screen, not the last one loaded.
     */
    internal val shownMonth: StateFlow<YearMonth?> = month.asStateFlow()

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

    private val removal = FriendRemoval(deleteReceivedPuzzle, appScope, store::discard)

    internal val friends: StateFlow<List<FriendCardModel>> = combine(friendPuzzles(), removal.hidden) { rows, hidden ->
        rows.filter { it.code !in hidden }
            .map { FriendCardModel(it, games.displayName(it.gameId), registry.brandOf(it.gameId)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The friend's puzzle just removed while its Undo is offered, else null. */
    internal val removedFriend: StateFlow<ShareCode?> = removal.removed

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

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val page: StateFlow<ArchivePageState?> =
        combine(selected, month, picked, ::Triple)
            .flatMapLatest { (game, shown, day) -> pageOf(game, shown, day) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Fetches today's uncached puzzles; a failure shows as that game's Offline row. */
    internal fun syncToday() {
        viewModelScope.launch { syncTodayPuzzles() }
    }

    /** [game]'s page opens (the tablet's right pane; the phone pushes it). */
    internal fun select(game: GameId) {
        if (game == selected.value) return
        selected.value = game
        backToToday()
    }

    /** The calendar shows [shown]; the day card keeps its day. */
    internal fun showMonth(shown: YearMonth) {
        month.value = shown
    }

    /** The day card shows [day]; today's own disc returns it to today. */
    internal fun pick(day: LocalDate) {
        picked.value = day.takeUnless { it == calendar.today(selected.value) }
    }

    /** The day card shows today and the calendar today's month. */
    internal fun backToToday() {
        picked.value = null
        month.value = null
    }

    /** Hides [code]'s puzzle from From friends and offers Undo; it is deleted when that closes. */
    internal fun removeFriend(code: ShareCode) {
        val card = friends.value.firstOrNull { it.row.code == code } ?: return
        removal.remove(code, card.row.gameId)
    }

    /** Cancels the pending delete and brings the just-removed card back. */
    internal fun undoRemoveFriend() = removal.undo()

    override fun onCleared() = removal.clear()

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

    private fun pageOf(game: GameId, shown: YearMonth?, day: LocalDate?): Flow<ArchivePageState?> {
        val target = shown ?: calendar.today(game)?.let(YearMonth::from) ?: return flowOf(null)
        val days = archiveMonth(game, target)
        if (day == null) return days.map { ArchivePageState(game, target, it, null) }
        val dayMonth = YearMonth.from(day)
        if (dayMonth == target) {
            return days.map { list -> ArchivePageState(game, target, list, list.firstOrNull { it.date == day }) }
        }
        // The picked day sits in another month: its own query keeps its status live.
        return combine(days, archiveMonth(game, dayMonth)) { list, other ->
            ArchivePageState(game, target, list, other.firstOrNull { it.date == day })
        }
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
