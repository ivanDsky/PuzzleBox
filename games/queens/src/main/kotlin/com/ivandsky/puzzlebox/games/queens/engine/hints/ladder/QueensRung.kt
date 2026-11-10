package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/** A Queens [LadderRung]: one deduction technique, applied one step at a time. */
interface QueensRung : LadderRung {
    /** Every deduction this rung finds, deterministic (lowest unit, then lowest cell), each with the cells it read. */
    fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof>

    /** First deduction of [steps], or null; implementations stop at it rather than list the rest. */
    fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction?

    /**
     * The [steps] a stepwise hint weighs: all of them, unless the rung knows some are the simpler
     * human step. [press] is the work the rungs asked within one press share.
     */
    fun simplestSteps(puzzle: QueensPuzzle, candidates: QueensCandidates, press: Press = Press()): Sequence<QueensProof> =
        steps(puzzle, candidates)

    /**
     * What the rungs share while one hint press asks them about one state, and nothing after: the
     * caller makes one per press and drops it. Holds the look-ahead chains, so D11 resumes where D10
     * stopped.
     */
    class Press {
        private var probe: LookAheadChain.Probe? = null

        /** The chains from every open cell of [candidates], played with [cheap]; kept while the same state is asked. */
        internal fun lookAhead(puzzle: QueensPuzzle, candidates: QueensCandidates, cheap: List<QueensRung>): LookAheadChain.Probe =
            probe?.takeIf { it.puzzle === puzzle && it.candidates === candidates && it.cheap === cheap }
                ?: LookAheadChain.Probe(puzzle, candidates, cheap).also { probe = it }
    }
}
