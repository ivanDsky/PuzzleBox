package com.ivandsky.puzzlebox.games.queens.engine.rules

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/** Rule 3: at most [QueensPuzzle.queensPerUnit] queens per color region (A9). */
class RegionExclusivityRule : QueensRule {
    override val id = QueensRuleId.RegionExclusivity

    override fun violated(cell: Cell, queens: Set<Cell>, puzzle: QueensPuzzle): Boolean {
        val region = puzzle.regionOf(cell)
        var count = 0
        for (q in queens) {
            if (puzzle.regionOf(q) == region && ++count == puzzle.queensPerUnit) return true
        }
        return false
    }

    override fun violations(queens: Set<Cell>, puzzle: QueensPuzzle): List<QueensRuleViolation> =
        unitViolations(queens, puzzle, id, puzzle::regionOf)
}
