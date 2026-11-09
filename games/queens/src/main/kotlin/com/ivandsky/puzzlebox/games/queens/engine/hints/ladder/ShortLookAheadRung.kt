package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * D10: an open cell is excluded when the [LookAheadChain] from a queen on it breaks within one
 * round ("a queen here forces a cell that leaves a unit no room"). One exclusion per such cell,
 * naming the broken unit; the evidence is that unit's open cells plus the queen it forced.
 * [cheap] is the rungs a chain round takes an exclusion from, passed in so no rung calls itself.
 *
 * Order: fewest rounds, then lowest cell.
 */
class ShortLookAheadRung(private val cheap: List<QueensRung>) : QueensRung {
    override val id: String = "short-look-ahead"
    override val level: Difficulty.Level = Difficulty.Level.Expert
    override val captionKey: String = "queens.hint.rung.short-look-ahead"

    // Capping the chains at one round gives the same breaks as a full chain filtered to ≤ 1 round, without the long tails.
    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        LookAheadChain.breaks(puzzle, candidates, cheap, cap = 1).asSequence().map { it.proof(this, puzzle, candidates) }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction
}
