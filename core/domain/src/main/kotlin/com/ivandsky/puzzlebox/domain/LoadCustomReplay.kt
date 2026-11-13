package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
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
     * A puzzle of another game is refused by the opener, not here.
     */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? {
        val (puzzle, newest) = when (ref) {
            is SolveReplayRef.Record -> ref.puzzle to false
            is SolveReplayRef.Latest -> ref.puzzle to true
            is SolveReplayRef.MyLeg, is SolveReplayRef.TheirLeg, is SolveReplayRef.Player -> return null
        }
        val key = puzzle.key as? PuzzleKey.Custom ?: return null
        return try {
            val bytes = (if (newest) completions.latestReplay(puzzle) else completions.recordReplay(puzzle))
                ?: return null
            val code = ShareCode.parse(key.puzzleId) ?: return null
            val row = received.get(code) ?: (loadShared(code) as? OpenOutcome.Opened)?.puzzle ?: return null
            ReplaySource(Puzzle(row.gameId, row.variant, row.sharedFormatVersion, row.puzzleBytes), bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
