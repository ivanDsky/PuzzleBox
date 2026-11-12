package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Latest
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Record
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlin.coroutines.cancellation.CancellationException

/** Finds the replay bytes of a daily solve. Answers only Record and Latest of a daily puzzle; every other ref is null before any read. */
class LoadDailyReplay(
    private val completions: CompletionRepository,
    private val daily: DailyRepository,
) {
    /** Null = no replay, for any cause (A13). The puzzle comes from the local cache only; a day the cache dropped has none. Cancellation still propagates. */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? {
        val puzzleRef = when (ref) {
            is Record -> ref.puzzle
            is Latest -> ref.puzzle
            else -> return null
        }
        val day = (puzzleRef.key as? PuzzleKey.Daily)?.date ?: return null
        return try {
            val bytes = (if (ref is Record) completions.recordReplay(puzzleRef) else completions.latestReplay(puzzleRef))
                ?: return null
            val puzzle = daily.cachedPuzzle(puzzleRef.gameId, day) ?: return null
            ReplaySource(puzzle, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
