package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.leaderboard.leaderboardOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handwritten fake for feature tests: in-memory boards, recorded submissions, scripted
 * submit results. Snapshots are always delivered versioned + sorted, like the real
 * backend module, so UI tests exercise the same shape.
 */
class FakeLeaderboardRepository(
    initialEntries: Map<LeaderboardRef, List<LeaderboardEntry>> = emptyMap(),
    var nextSubmitResult: (LeaderboardEntry) -> SubmitResult = { SubmitResult.Ok(it) },
) : LeaderboardRepository {

    private val boards = MutableStateFlow(initialEntries)

    private val flows = mutableMapOf<LeaderboardRef, MutableStateFlow<LeaderboardSnapshot?>>()

    val submitted = mutableListOf<LeaderboardEntry>()

    val observedBoards = mutableListOf<LeaderboardRef>()

    /** Simulate other players (or a refresh) arriving on a board. */
    fun setEntries(board: LeaderboardRef, entries: List<LeaderboardEntry>) {
        boards.value = boards.value + (board to entries.sortedWith(leaderboardOrder))
        flows[board]?.value = LeaderboardSnapshot(board, entries.sortedWith(leaderboardOrder))
    }

    override fun observe(board: LeaderboardRef): Flow<LeaderboardSnapshot?> {
        observedBoards += board
        // One live flow per board, like the real backend module's snapshot listener.
        return flows.getOrPut(board) {
            MutableStateFlow(boards.value[board]?.let { LeaderboardSnapshot(board, it) })
        }.asStateFlow()
    }

    override suspend fun submit(entry: LeaderboardEntry): SubmitResult {
        submitted += entry
        return nextSubmitResult(entry)
    }
}
