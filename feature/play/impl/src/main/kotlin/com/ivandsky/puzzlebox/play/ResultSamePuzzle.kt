package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.resultui.SamePuzzleSolves
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

/**
 * Result's same-puzzle comparison for one [solve]: the player's earlier solves of the same puzzle, whatever their source
 * (the record, play-agains and a LinkedIn import alike), without this solve's own row. [live] is the slot's live play only
 * when it held this solve as Result opened; null otherwise (as after process death), where the solve is taken as stored.
 */
internal class ResultSamePuzzle(
    private val completions: CompletionRepository,
    private val solve: ActivePlay.Solve,
    private val live: ActivePlay?,
    private val order: RankOrder,
) {
    fun of(ref: PuzzleRef): Flow<SamePuzzleSolves> =
        combine(completions.observeSolves(ref), storedOf()) { rows, stored ->
            val earlier = if (stored) rows.withoutOwn() else rows
            val previous = earlier.firstOrNull() ?: return@combine SamePuzzleSolves.None
            SamePuzzleSolves(
                previous = BestScore(previous.time, previous.hints),
                previousFromLinkedIn = previous.source == CompletionSource.LinkedIn,
                best = earlier.map { BestScore(it.time, it.hints) }.minWithOrNull(order.comparator),
            )
        }

    /**
     * True once this solve's row is stored: a live play holding the solve says so itself ([ActivePlay.Record.stored]), so an
     * older row with the same time and hints is never taken for it; with no live play the solve is long stored.
     */
    private fun storedOf(): Flow<Boolean> =
        live?.record?.map { it.solve == solve && it.stored }?.runningFold(false) { seen, now -> seen || now }
            ?: flowOf(true)

    /** The rows, newest first, without the newest one that is this solve (a row has no id, so its time and hints name it). */
    private fun List<CompletionRecord>.withoutOwn(): List<CompletionRecord> {
        val own = indexOfFirst { it.time == solve.timeMs && it.hints == solve.hints }
        return if (own < 0) this else filterIndexed { index, _ -> index != own }
    }
}
