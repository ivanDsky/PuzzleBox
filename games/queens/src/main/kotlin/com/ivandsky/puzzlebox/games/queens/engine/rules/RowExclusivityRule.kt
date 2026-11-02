package com.ivandsky.puzzlebox.games.queens.engine.rules

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/** Rule 1: at most [QueensPuzzle.queensPerUnit] queens per row (A9 parameterized). */
class RowExclusivityRule : QueensRule {
    override val id = QueensRuleId.RowExclusivity

    override fun violated(cell: Cell, queens: Set<Cell>, puzzle: QueensPuzzle): Boolean {
        val row = puzzle.rowOf(cell)
        var count = 0
        for (q in queens) {
            if (puzzle.rowOf(q) == row && ++count == puzzle.queensPerUnit) return true
        }
        return false
    }

    override fun violations(queens: Set<Cell>, puzzle: QueensPuzzle): List<QueensRuleViolation> =
        unitViolations(queens, puzzle, id, puzzle::rowOf)
}
