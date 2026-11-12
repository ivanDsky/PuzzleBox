package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay
import kotlinx.coroutines.flow.Flow

/**
 * Records + play-agains store (ADR-0004; A4/A5). PINNED ticket-15 contract — ticket 13 codes
 * against this exact signature; drift = integration failure. Implementation: Room
 * ([RoomCompletionRepository]), DI singleton.
 */
interface CompletionRepository {
    /** First completion inserts with isRecord=true; every later play of the same
     *  puzzleRef stores a play-again row and NEVER mutates the record (A4).
     *  [replay]: the solve's sealed replay, stored beside the completion in the same transaction. Null = none. */
    suspend fun onCompleted(record: CompletionRecord, replay: SolveReplay? = null)

    fun observeRecords(): Flow<List<CompletionRecord>> // record rows only

    /** Every LinkedIn import, records and play-agains alike. */
    fun observeImports(): Flow<List<CompletionRecord>>

    fun observePlayAgains(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>>

    /** Record and play-again rows of one friend puzzle (ref key `custom/<gameId>/<puzzleId>`), newest completedAt first. */
    fun observeCustom(gameId: GameId, puzzleId: String): Flow<List<CompletionRecord>>

    /** The game's best (spec A10, A25): the top record row (first solves) of [game] by the
     *  game's own rank order (ADR-0013) — play-agains never rank; null = no completion yet. */
    suspend fun best(game: GameId): BestScore?

    /** Replay of the puzzle's record solve (the first win); null when it has none. */
    suspend fun recordReplay(puzzleRef: PuzzleRef): SolveReplay?

    /** Replay of the puzzle's newest solve; null when it has none. */
    suspend fun latestReplay(puzzleRef: PuzzleRef): SolveReplay?

    /** True when the puzzle's newest solve is its record solve, or it has no solve. */
    suspend fun latestIsRecord(puzzleRef: PuzzleRef): Boolean
}
