package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.leaderboard.entryComparator
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CustomBoardRepository] that passes the same contract as the Firestore adapter, plus
 * knobs for what a device cannot force on demand. Boards are keyed by code alone, as in
 * Firestore; [observe] is live and re-ranks on every change of entries or knobs.
 */
class FakeCustomBoardRepository : CustomBoardRepository {

    private data class State(
        val boards: Map<ShareCode, List<LeaderboardEntry>> = emptyMap(),
        val online: Boolean = true,
        val signedIn: Boolean = true,
    )

    private val state = MutableStateFlow(State())

    /** Every board by code, entries in submit order. */
    val entries: Map<ShareCode, List<LeaderboardEntry>> get() = state.value.boards

    /** False makes observe emit null, submit Err(Unavailable) and solvesCount null. */
    var online: Boolean
        get() = state.value.online
        set(value) = state.update { it.copy(online = value) }

    /** False ⇒ observe emits null, submit returns Err(Unavailable); reads are denied, so solvesCount is null too. */
    var signedIn: Boolean
        get() = state.value.signedIn
        set(value) = state.update { it.copy(signedIn = value) }

    /** True ⇒ submit returns Err(Rejected) and writes nothing, like a rules refusal. */
    @Volatile var rejectSubmits: Boolean = false

    override fun observe(gameId: GameId, code: ShareCode, order: RankOrder, limit: Int): Flow<List<LeaderboardEntry>?> =
        state.map { current ->
            if (!current.online || !current.signedIn) {
                null
            } else {
                current.boards[code].orEmpty().sortedWith(order.entryComparator().thenBy { it.completedAtMs }.thenBy { it.uid }).take(limit)
            }
        }.distinctUntilChanged()

    override suspend fun submit(code: ShareCode, entry: LeaderboardEntry): SubmitResult {
        var result: SubmitResult = SubmitResult.Ok(entry)
        state.update { current ->
            val board = current.boards[code].orEmpty()
            val existing = board.firstOrNull { it.uid == entry.uid }
            result = when {
                !current.online -> SubmitResult.Err(SubmitError.Unavailable("offline"))
                !current.signedIn -> SubmitResult.Err(SubmitError.Unavailable("signed out"))
                rejectSubmits -> SubmitResult.Err(SubmitError.Rejected("rejected by rules"))
                existing != null -> SubmitResult.Deduped(existing)
                else -> SubmitResult.Ok(entry)
            }
            if (result is SubmitResult.Ok) current.copy(boards = current.boards + (code to board + entry)) else current
        }
        return result
    }

    override suspend fun solvesCount(code: ShareCode): Int? {
        val current = state.value
        if (!current.online || !current.signedIn) return null
        return current.boards[code].orEmpty().size
    }
}
