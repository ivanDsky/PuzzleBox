package com.ivandsky.puzzlebox.games.queens.engine.hints.step

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensProof

/** The stepwise hint step this proof is: the stepper shows it, the Trainer's move check hands it to the reveal. */
internal fun QueensProof.toStep(): QueensStep.Deduction {
    val result = when (val d = deduction) {
        is QueensDeduction.Place -> QueensStep.Deduction.Result.LastCell(Cell(d.cell))
        is QueensDeduction.Exclude -> QueensStep.Deduction.Result.Cross(d.cells.mapTo(HashSet(d.cells.size * 2), ::Cell))
    }
    return QueensStep.Deduction(deduction.unit, evidence.mapTo(HashSet(evidence.size * 2), ::Cell), result)
}
