package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow

/**
 * Records + replays store (ADR-0004; A4/A5). PINNED ticket-15 contract — ticket 13 codes
 * against this exact signature; drift = integration failure. Implementation: Room
 * ([RoomCompletionRepository]), DI singleton.
 */
interface CompletionRepository {
    /** First completion inserts with isRecord=true; every later play of the same
     *  puzzleRef stores a replay row and NEVER mutates the record (A4). */
    suspend fun onCompleted(record: CompletionRecord)

    fun observeRecords(): Flow<List<CompletionRecord>> // record rows only

    fun observeReplays(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>>

    /** The game's best time (spec A10): the minimum over the record rows (first solves)
     *  of [game] — replays never rank; null = the game has no completion yet. */
    suspend fun bestTimeMs(game: GameId): Long?
}
