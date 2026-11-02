package com.ivandsky.puzzlebox.games.queens.engine.rules

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * Shared scan for the three unit-count rules (row / column / region): group the placed
 * queens by unit id, and flag every queen in a unit holding more than
 * [QueensPuzzle.queensPerUnit] queens (A9). Cell attribution: each queen of an
 * over-full unit is a violation of that unit's rule.
 */
internal fun unitViolations(
    queens: Set<Cell>,
    puzzle: QueensPuzzle,
    rule: QueensRuleId,
    unitOf: (Cell) -> Int,
): List<QueensRuleViolation> {
    val byUnit = HashMap<Int, MutableList<Cell>>(puzzle.size)
    for (q in queens) byUnit.getOrPut(unitOf(q)) { mutableListOf() }.add(q)
    val out = mutableListOf<QueensRuleViolation>()
    for (group in byUnit.values) {
        if (group.size > puzzle.queensPerUnit) {
            for (q in group) out.add(QueensRuleViolation(rule, q))
        }
    }
    return out
}
