package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import kotlin.coroutines.cancellation.CancellationException

/** Finds the replay bytes of a custom-puzzle solve. Answers only Record and Latest of a custom puzzle; every other ref is null before any read. */
class LoadCustomReplay(
    private val completions: CompletionRepository,
    private val received: ReceivedPuzzleRepository,
    private val loadShared: LoadSharedPuzzle,
) {
    /**
     * Null = no replay: not a custom Record or Latest, no stored bytes, or the puzzle not loadable here
     * (the store first, then the shared loader, as play opens it). Never throws; cancellation propagates.
     */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? {
        val puzzle = when (ref) {
            is SolveReplayRef.Record -> ref.puzzle
            is SolveReplayRef.Latest -> ref.puzzle
            is SolveReplayRef.MyLeg, is SolveReplayRef.TheirLeg -> return null
        }
        val key = puzzle.key as? PuzzleKey.Custom ?: return null
        return try {
            val bytes: SolveReplay = when (ref) {
                is SolveReplayRef.Record -> completions.recordReplay(puzzle)
                is SolveReplayRef.Latest -> completions.latestReplay(puzzle)
                is SolveReplayRef.MyLeg, is SolveReplayRef.TheirLeg -> null
            } ?: return null
            val code = ShareCode.parse(key.puzzleId) ?: return null
            val row = received.get(code) ?: (loadShared(code) as? OpenOutcome.Opened)?.puzzle ?: return null
            if (row.gameId != puzzle.gameId) return null
            ReplaySource(Puzzle(row.gameId, row.variant, row.sharedFormatVersion, row.puzzleBytes), bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
