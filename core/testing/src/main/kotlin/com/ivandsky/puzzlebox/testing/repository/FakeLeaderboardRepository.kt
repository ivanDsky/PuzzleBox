package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.leaderboard.entryComparator
import com.ivandsky.puzzlebox.game.RankOrder
import java.util.concurrent.CopyOnWriteArrayList
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

    override suspend fun submit(entry: LeaderboardEntry): SubmitResult {
        submitted += entry
        return nextSubmitResult(entry)
    }
}
