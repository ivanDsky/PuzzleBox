package com.ivandsky.puzzlebox.games.queens.engine.rules

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * Rule 4: no-touch adjacency (A1, research 01 §1.2) — a queen blocks its EIGHT
 * neighboring cells (orthogonal + diagonal corner touches). The full chess diagonal is
 * NEVER a conflict; distant diagonal cells are valid. N-independent (research 01 §2).
 */
class NoTouchAdjacencyRule : QueensRule {
    override val id = QueensRuleId.NoTouchAdjacency

    override fun violated(cell: Cell, queens: Set<Cell>, puzzle: QueensPuzzle): Boolean {
        for (q in queens) if (QueensVerifier.areNeighbors(cell, q, puzzle.size)) return true
        return false
    }

    override fun violations(queens: Set<Cell>, puzzle: QueensPuzzle): List<QueensRuleViolation> {
        val placed = queens.sortedBy { it.idx }
        val out = mutableListOf<QueensRuleViolation>()
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                if (QueensVerifier.areNeighbors(placed[i], placed[j], puzzle.size)) {
                    out.add(QueensRuleViolation(id, placed[i]))
                    out.add(QueensRuleViolation(id, placed[j]))
                }
            }
        }
        return out
    }
}
