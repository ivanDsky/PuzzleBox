package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow

/**
 * The board of one friend puzzle (spec A20): one first-win entry per uid under its share code.
 * Implementations pass `CustomBoardRepositoryContract` (core:testing).
 */
interface CustomBoardRepository {
    /** Ranked by [order] (A20); null = offline/unreadable (signed out reads are denied by rules → null too). */
    fun observe(gameId: GameId, code: ShareCode, order: RankOrder, limit: Int): Flow<List<LeaderboardEntry>?>

    /** Create-if-absent at puzzles/{code}/entries/{uid}; an existing entry ⇒ SubmitResult.Deduped. */
    suspend fun submit(code: ShareCode, entry: LeaderboardEntry): SubmitResult

    /** Firestore count() over puzzles/{code}/entries; null when offline. */
    suspend fun solvesCount(code: ShareCode): Int?
}
