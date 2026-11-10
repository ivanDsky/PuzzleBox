package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * D4: a row, column or region still missing queens, with at least 2 open cells, would be
 * left no room by a queen on any open cell that touches all of them, so each such cell is
 * excluded. One exclusion per unit, naming it; a unit down to one open cell is
 * [SinglesRung]'s. The 2×2 rule is a case of this: the four cells of a 2×2 all touch.
 *
 * Order: ROW → COLUMN → REGION, then lowest unit index.
 */
object SharedNeighboursRung : QueensRung {
    override val id: String = "shared-neighbours"
    override val level: Difficulty.Level = Difficulty.Level.Medium
    override val captionKey: String = "queens.hint.rung.shared-neighbours"

    /** Lazy: one unit at a time, so [step] stops at the first. */
    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> = sequence {
        val n = puzzle.size
        for (kind in HintReason.Kind.entries) {
            val units = QueensUnits.cells(puzzle, kind)
            for (index in units.indices) {
                val cells = units[index]
                val missing = candidates.missing(puzzle, cells)
                val open = cells.filter(candidates::isOpen)
                if (missing <= 0 || open.size < 2) continue
                // A cell never touches itself, so no open cell of the unit can touch all of them.
                val smothering = (0 until n * n).filter { cell ->
                    candidates.isOpen(cell) && open.all { QueensAttack.touches(cell, it, n) }
                }.toSet()
                if (smothering.isNotEmpty()) {
                    yield(QueensProof(QueensDeduction.Exclude(this@SharedNeighboursRung, HintReason(kind, index, this@SharedNeighboursRung), smothering), open.toSet()))
                }
            }
        }
    }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction
}
