package com.ivandsky.puzzlebox.games.queens.engine.rules

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/** Rule 2: at most [QueensPuzzle.queensPerUnit] queens per column (A9 parameterized). */
class ColumnExclusivityRule : QueensRule {
    override val id = QueensRuleId.ColumnExclusivity

    override fun violated(cell: Cell, queens: Set<Cell>, puzzle: QueensPuzzle): Boolean {
        val col = puzzle.colOf(cell)
        var count = 0
        for (q in queens) {
            if (puzzle.colOf(q) == col && ++count == puzzle.queensPerUnit) return true
        }
        return false
    }

    override fun violations(queens: Set<Cell>, puzzle: QueensPuzzle): List<QueensRuleViolation> =
        unitViolations(queens, puzzle, id, puzzle::colOf)
}
