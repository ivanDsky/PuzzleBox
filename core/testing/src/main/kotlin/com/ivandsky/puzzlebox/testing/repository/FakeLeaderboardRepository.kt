package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.leaderboard.entryComparator
import com.ivandsky.puzzlebox.game.RankOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Handwritten fake for feature tests: in-memory boards, recorded submissions, scripted
 * submit results. Snapshots are always delivered versioned + sorted by the observer's
 * [RankOrder], like the real backend module, so UI tests exercise the same shape.
 */
class FakeLeaderboardRepository(
    private val initialEntries: Map<LeaderboardRef, List<LeaderboardEntry>> = emptyMap(),
    var nextSubmitResult: (LeaderboardEntry) -> SubmitResult = { SubmitResult.Ok(it) },
) : LeaderboardRepository {

    private val flows = mutableMapOf<LeaderboardRef, MutableStateFlow<List<LeaderboardEntry>?>>()

    private fun flowOf(board: LeaderboardRef) =
        flows.getOrPut(board) { MutableStateFlow(initialEntries[board]) }

    /** Thread-safe: submitters launch each submit on the default dispatcher, so submits can overlap. */
    val submitted: MutableList<LeaderboardEntry> = CopyOnWriteArrayList()

    val observedBoards = mutableListOf<LeaderboardRef>()

    /** Simulate other players (or a refresh) arriving on a board; observers get them ranked by their order. */
    fun setEntries(board: LeaderboardRef, entries: List<LeaderboardEntry>) {
        flowOf(board).value = entries
    }

    override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> {
        observedBoards += board
        // One live source per board, like the real backend module's snapshot listener.
        return flowOf(board).map { entries ->
            entries?.let { LeaderboardSnapshot(board, it.sortedWith(order.entryComparator())) }
        }
    }

    /** Whole-board N per board, as the server count would report it; absent = the entries' size. */
    private val totals = mutableMapOf<LeaderboardRef, Int>()

    /** Make [board] longer than its stored page, so `count` and a full-page `fetch` report [total]. */
    fun setCount(board: LeaderboardRef, total: Int) {
        totals[board] = total
    }

    /** When false, `fetch` and `count` answer null, like a signed-out or failed read. */
    var readsAvailable = true

    private val fetches = AtomicInteger()
    private val counts = AtomicInteger()
    val fetchCalls: Int get() = fetches.get()
    val countCalls: Int get() = counts.get()

    override suspend fun fetch(board: LeaderboardRef, order: RankOrder): BoardPage? {
        fetches.incrementAndGet()
        if (!readsAvailable) return null
        val entries = flowOf(board).value.orEmpty().sortedWith(order.entryComparator())
        return BoardPage(entries, totals[board]?.takeIf { entries.size >= BoardPage.PAGE_SIZE } ?: entries.size)
    }

    override suspend fun count(board: LeaderboardRef): Int? {
        counts.incrementAndGet()
        if (!readsAvailable) return null
        return totals[board] ?: flowOf(board).value.orEmpty().size
    }

    override suspend fun submit(entry: LeaderboardEntry): SubmitResult {
        submitted += entry
        return nextSubmitResult(entry)
    }
}
