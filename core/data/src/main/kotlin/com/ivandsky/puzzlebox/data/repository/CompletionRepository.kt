package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.BestScore
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

    /** Every LinkedIn import, records and replays alike. */
    fun observeImports(): Flow<List<CompletionRecord>>

    fun observePlayAgains(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>>

    /** Record and replay rows of one friend puzzle (ref key `custom/<gameId>/<puzzleId>`), newest completedAt first. */
    fun observeCustom(gameId: GameId, puzzleId: String): Flow<List<CompletionRecord>>

    /** The game's best (spec A10, A25): the top record row (first solves) of [game] by the
     *  game's own rank order (ADR-0013) — replays never rank; null = no completion yet. */
    suspend fun best(game: GameId): BestScore?
}
