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
        LookAheadChain.Probe(puzzle, candidates, cheap).breaks(LookAheadChain.CAP)
            .filter { it.rounds >= 2 }
            .map { it.proof(this, puzzle, candidates) }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction

    /**
     * A shorter chain is the simpler human step, so only the chains that break at the fewest
     * rounds; they stop at that depth rather than run every chain to its end (Main ruling on 13).
     * On a [press] D10 already asked, the chains resume where D10 stopped.
     */
    override fun simplestSteps(puzzle: QueensPuzzle, candidates: QueensCandidates, press: QueensRung.Press): Sequence<QueensProof> =
        stepsAtFirstBreak(puzzle, candidates, press).asSequence()

    /** [simplestSteps] as a list, lowest cell first, on [press]'s chains. */
    internal fun stepsAtFirstBreak(puzzle: QueensPuzzle, candidates: QueensCandidates, press: QueensRung.Press = QueensRung.Press()): List<QueensProof> =
        press.lookAhead(puzzle, candidates, cheap).atFirstDepth(minRounds = 2, maxRounds = LookAheadChain.CAP)
            .map { it.proof(this, puzzle, candidates) }
}
