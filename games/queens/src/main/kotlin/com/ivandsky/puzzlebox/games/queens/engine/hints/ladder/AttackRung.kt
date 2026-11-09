package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * D0, for play with Auto-cross off: every open cell a standing queen rules out, one exclusion
 * per queen, naming the queen's row. Touching cells always go; the queen's row, column and
 * region go only once that unit holds its queens, the closing rule of [QueensCandidates.place],
 * so an N=2 board is not over-crossed. After `place` nothing is left to list, so this fires
 * only for queens stood without their side exclusions.
 *
 * Order: ascending queen cell, which is lowest row, then lowest cell.
 */
object AttackRung : QueensRung {
    override val id: String = "attack"
    override val level: Difficulty.Level = Difficulty.Level.Easy
    override val captionKey: String = "queens.hint.rung.attack"

    /** Lazy: one queen at a time, so [step] stops at the first. */
    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> {
        val n = puzzle.size
        fun full(sameUnit: (Int) -> Boolean): Boolean = candidates.queens.count(sameUnit) >= puzzle.queensPerUnit
        return candidates.queens.sorted().asSequence().mapNotNull { queen ->
            val sameRow = { cell: Int -> cell / n == queen / n }
            val sameColumn = { cell: Int -> cell % n == queen % n }
            val sameRegion = { cell: Int -> puzzle.regions[cell] == puzzle.regions[queen] }
            val closed = listOf(sameRow, sameColumn, sameRegion).filter(::full)
            val cells = (0 until n * n).filter { cell ->
                candidates.isOpen(cell) && (QueensAttack.touches(queen, cell, n) || closed.any { it(cell) })
            }.toSet()
            if (cells.isEmpty()) {
                null
            } else {
                QueensProof(QueensDeduction.Exclude(this, HintReason(HintReason.Kind.ROW, queen / n, this), cells), setOf(queen))
            }
        }
    }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction
}
