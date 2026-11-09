package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * D8: a row, column or region still missing queens, with at least 2 open cells, would be
 * left no room by a queen on any open cell outside it that rules out all of them, so each
 * such cell is excluded. One exclusion per unit, naming it; a unit down to one open cell is
 * [SinglesRung]'s. A queen rules out what [QueensCandidates.place] crosses: the cells it
 * touches, and the cells of a row, column or region it fills.
 *
 * Order: ROW → COLUMN → REGION, then lowest unit index.
 */
object SqueezeRung : QueensRung {
    override val id: String = "squeeze"
    override val level: Difficulty.Level = Difficulty.Level.Hard
    override val captionKey: String = "queens.hint.rung.squeeze"

    /** Lazy: one unit at a time, so [step] stops at the first. */
    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> {
        val n = puzzle.size
        val units = List(n) { row -> HintReason(HintReason.Kind.ROW, row, this) to List(n) { row * n + it } } +
            List(n) { col -> HintReason(HintReason.Kind.COLUMN, col, this) to List(n) { it * n + col } } +
            List(n) { region -> HintReason(HintReason.Kind.REGION, region, this) to puzzle.regions.indices.filter { puzzle.regions[it] == region } }
        return units.asSequence().mapNotNull { (unit, cells) ->
            val missing = puzzle.queensPerUnit - cells.count { it in candidates.queens }
            val open = cells.filter(candidates::isOpen)
            if (missing <= 0 || open.size < 2) return@mapNotNull null
            val squeezing = puzzle.regions.indices.filter { x ->
                candidates.isOpen(x) && x !in cells && open.all { rulesOut(puzzle, candidates, x, it) }
            }.toSet()
            if (squeezing.isEmpty()) null else QueensProof(QueensDeduction.Exclude(this, unit, squeezing), open.toSet())
        }
    }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        steps(puzzle, candidates).firstOrNull()?.deduction

    /** A queen on [x] crosses [cell]; on one-queen boards this is [QueensAttack.attacks]. */
    private fun rulesOut(puzzle: QueensPuzzle, candidates: QueensCandidates, x: Int, cell: Int): Boolean {
        val n = puzzle.size
        val others = puzzle.queensPerUnit - 1
        return QueensAttack.touches(x, cell, n) ||
            x / n == cell / n && candidates.queens.count { it / n == x / n } >= others ||
            x % n == cell % n && candidates.queens.count { it % n == x % n } >= others ||
            puzzle.regions[x] == puzzle.regions[cell] && candidates.queens.count { puzzle.regions[it] == puzzle.regions[x] } >= others
    }
}
