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

    // Lazy: step() stops at the first break, often a round-0 one, without probing the other cells.
    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        LookAheadChain.Probe(puzzle, candidates, cheap).breaks(maxRounds = 1).map { it.proof(this, puzzle, candidates) }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction

    /**
     * A shorter chain is the simpler human step, so only the chains that break at the fewest
     * rounds; they stop at that depth rather than run every chain to its end (Main ruling on 13).
     */
    override fun simplestSteps(puzzle: QueensPuzzle, candidates: QueensCandidates, press: QueensRung.Press): Sequence<QueensProof> =
        stepsAtFirstBreak(puzzle, candidates, press).asSequence()

    /** [simplestSteps] as a list, lowest cell first, on [press]'s chains. */
    internal fun stepsAtFirstBreak(puzzle: QueensPuzzle, candidates: QueensCandidates, press: QueensRung.Press = QueensRung.Press()): List<QueensProof> =
        press.lookAhead(puzzle, candidates, cheap).atFirstDepth(minRounds = 0, maxRounds = 1).map { it.proof(this, puzzle, candidates) }
}
