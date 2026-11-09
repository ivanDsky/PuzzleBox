package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * D11: an open cell is excluded when the [LookAheadChain] from a queen on it breaks, but only
 * after two or more rounds (within [LookAheadChain.CAP]); one round or fewer is
 * [ShortLookAheadRung]'s. A cell whose chain proves nothing is never excluded. One exclusion
 * per such cell, naming the broken unit; the evidence is that unit's open cells plus every
 * queen the chain forced. [cheap] is the rungs a chain round takes an exclusion from.
 *
 * Order: fewest rounds, then lowest cell.
 */
class LongLookAheadRung(private val cheap: List<QueensRung>) : QueensRung {
    override val id: String = "long-look-ahead"
    override val level: Difficulty.Level = Difficulty.Level.Expert
    override val captionKey: String = "queens.hint.rung.long-look-ahead"

    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        LookAheadChain.breaks(puzzle, candidates, cheap, LookAheadChain.CAP)
            .filter { it.rounds >= 2 }
            .map { it.proof(this, puzzle, candidates) }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction

    /** The [steps] whose chains break at the fewest rounds, lowest cell first. */
    internal fun stepsAtFirstBreak(puzzle: QueensPuzzle, candidates: QueensCandidates): List<QueensProof> =
        LookAheadChain.breaksAtFirstDepth(puzzle, candidates, cheap, minRounds = 2, maxRounds = LookAheadChain.CAP)
            .map { it.proof(this, puzzle, candidates) }
}
