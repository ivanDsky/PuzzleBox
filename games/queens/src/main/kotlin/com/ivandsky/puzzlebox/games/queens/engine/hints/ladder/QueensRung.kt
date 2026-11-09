package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/** A Queens [LadderRung]: one deduction technique, applied one step at a time. */
interface QueensRung : LadderRung {
    /** Every deduction this rung finds, deterministic (lowest unit, then lowest cell), each with the cells it read. */
    fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof>

    /** First deduction of [steps], or null; implementations stop at it rather than list the rest. */
    fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction?
}
