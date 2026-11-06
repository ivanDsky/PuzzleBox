package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/** A Queens [LadderRung]: one deduction technique, applied one step at a time. */
interface QueensRung : LadderRung {
    /** First deduction this rung finds, deterministic (lowest unit, then lowest cell), or null. */
    fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction?
}
