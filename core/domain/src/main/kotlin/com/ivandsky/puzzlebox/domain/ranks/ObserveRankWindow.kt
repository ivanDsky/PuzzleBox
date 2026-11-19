package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.FinalBoardStore
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.entryComparator
import com.ivandsky.puzzlebox.data.leaderboard.isFinal
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/**
 * Every daily board of [RankWindow] × games within the read budget (A23): an open board holds
 * one listener for the collection's lifetime, a Final board (A22) is read from [FinalBoardStore]
 * or fetched once and stored, and N costs a count only for an open board at the page cap.
 *
 * Stateless: each collection attaches its own listeners, so the caller shares one collection
 * (the hub's `stateIn(WhileSubscribed)`) rather than collecting per reader.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveRankWindow(
    private val identity: IdentityService,
    private val leaderboard: LeaderboardRepository,
    private val finalBoards: FinalBoardStore,
    private val registry: GameRegistry,
    private val calendar: GameCalendar,
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * Each [refresh] tick re-reads what failed: an Unavailable Final board is fetched again and a
     * capped open board is re-counted, and an open board whose listener ended degraded (the repository's
     * terminal null) listens again; a healthy listener is never re-attached. [refresh] is collected
     * once per collection of the result, however many boards the window has.
     */
    operator fun invoke(
        window: RankWindow,
        games: List<GameId>,
        refresh: Flow<Unit> = emptyFlow(),
    ): Flow<RankWindowBoards> = identity.current
        // Keyed on the uid: a renamed player must not re-attach every listener.
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid -> if (uid == null) flowOf(RankWindowBoards.SignedOut) else boards(window, games, refresh) }

    private fun boards(window: RankWindow, games: List<GameId>, refresh: Flow<Unit>): Flow<RankWindowBoards> = flow {
        coroutineScope {
            // One collector of the caller's refresh fanned out to every board: the caller's
            // backoff is one schedule, and a collector per board would start one each.
            val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            val refreshing = launch { refresh.collect(ticks) }
            emitAll(games.map { gameBoards(it, window, ticks) }.latest().map { RankWindowBoards.Boards(it.flatten()) })
            refreshing.cancel()
        }
    }

    /**
     * A board exists only up to its game's own puzzle day, so the window is clipped per game; a
     * rollover restarts the game's boards only when it adds a day to the window.
     */
    private fun gameBoards(game: GameId, window: RankWindow, ticks: Flow<Unit>): Flow<List<BoardLoad>> {
        val order = registry.rankOrderOf(game)
        return calendar.observeToday(game)
            .map { window.days(it) }
            .distinctUntilChanged()
            .flatMapLatest { days ->
                val utcToday = LocalDate.now(clock.withZone(ZoneOffset.UTC))
                days.map { board(LeaderboardRef(game.value, it), order, utcToday, ticks) }.latest()
            }
            // An unregistered game has no day; without this its silence would hold back every other game.
            .onEmpty { emit(emptyList()) }
    }

    private fun board(board: LeaderboardRef, order: RankOrder, utcToday: LocalDate, ticks: Flow<Unit>): Flow<BoardLoad> {
        val reads = if (board.isFinal(utcToday)) finalBoard(board, order, ticks) else openBoard(board, order, ticks)
        return reads.onStart { emit(BoardLoad.Loading(board, order)) }.distinctUntilChanged()
    }

    /** One read on attach and one per refresh while it fails; a loaded Final board is never read again. */
    private fun finalBoard(board: LeaderboardRef, order: RankOrder, ticks: Flow<Unit>): Flow<BoardLoad> =
        ticks.rounds()
            .conflate()
            .map { readFinal(board, order) }
            .transformWhile { load ->
                emit(load)
                load is BoardLoad.Unavailable
            }

    private suspend fun readFinal(board: LeaderboardRef, order: RankOrder): BoardLoad {
        // The store keeps no rank order, so a stored page is ranked again on the way out.
        val stored = finalBoards.get(board)?.let { it.copy(entries = it.entries.sortedWith(order.entryComparator())) }
        // A failed fetch is null and is not stored; a fetched empty board is a real answer and is (A13).
        val page = stored
            ?: leaderboard.fetch(board, order)?.also { finalBoards.put(board, it) }
            ?: return BoardLoad.Unavailable(board, order)
        return BoardLoad.Loaded(board, order, page, final = true)
    }

    private fun openBoard(board: LeaderboardRef, order: RankOrder, ticks: Flow<Unit>): Flow<BoardLoad> = flow {
        var countedRound: Int? = null
        var total: Int? = null
        listen(board, order, ticks).combine(ticks.rounds(), ::Pair).collect { (snapshot, round) ->
            // An empty answer from the cache says nothing about the board: not known yet, like no snapshot.
            val entries = snapshot?.entries?.takeUnless { snapshot.fromCache && it.isEmpty() }
            val count = when {
                entries == null -> null
                // Below the cap the page is the whole board, so its length is N.
                entries.size < BoardPage.PAGE_SIZE -> entries.size
                else -> {
                    // One count on reaching the cap and one per refresh, not one per snapshot.
                    if (countedRound != round) {
                        countedRound = round
                        leaderboard.count(board)?.let { total = it }
                    }
                    // A12: without N the percentile would be wrong, so the board is unavailable rather than guessed.
                    total?.let { maxOf(it, entries.size) }
                }
            }
            emit(
                if (entries == null || count == null) {
                    BoardLoad.Unavailable(board, order)
                } else {
                    BoardLoad.Loaded(board, order, BoardPage(entries, count), final = false)
                },
            )
        }
    }

    /** The board's listener; one that ends degraded (last answer null) is attached again on the next tick. */
    private fun listen(board: LeaderboardRef, order: RankOrder, ticks: Flow<Unit>): Flow<LeaderboardSnapshot?> = flow {
        while (true) {
            var last: LeaderboardSnapshot? = null
            leaderboard.observe(board, order).collect { snapshot ->
                last = snapshot
                emit(snapshot)
            }
            if (last != null) return@flow
            ticks.first()
        }
    }

    private fun Flow<Unit>.rounds(): Flow<Int> = runningFold(0) { round, _ -> round + 1 }

    /** `combine` of no flows never emits; an empty window still has an answer. */
    private inline fun <reified T> List<Flow<T>>.latest(): Flow<List<T>> =
        if (isEmpty()) flowOf(emptyList()) else combine(this) { it.toList() }
}
