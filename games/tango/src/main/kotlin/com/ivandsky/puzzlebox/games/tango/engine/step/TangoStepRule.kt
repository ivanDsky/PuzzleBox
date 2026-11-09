package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/** One stepwise deduction: every cell it proves on [grid], one [TangoStep.Deduction] per cell. */
fun interface TangoStepRule {
    fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoStep.Deduction>
}
