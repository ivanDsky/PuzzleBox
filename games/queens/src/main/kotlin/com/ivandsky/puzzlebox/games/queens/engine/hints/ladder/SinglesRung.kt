package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * First rung: a row, column or region whose missing queens fit only its open cells
 * (for N=1: one open cell left) places its lowest open cell.
 *
 * Order, the one today's hints use: lowest region of the placed cell (the region the
 * hint highlights), then lowest cell, then the ROW → COLUMN → REGION proof.
 */
object SinglesRung : QueensRung {
    override val id: String = "singles"
    override val level: Difficulty.Level = Difficulty.Level.Easy
    override val captionKey: String = "queens.hint.rung.singles"

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? {
        val n = puzzle.size
        val units = List(n) { row -> HintReason(HintReason.Kind.ROW, row) to List(n) { row * n + it } } +
            List(n) { col -> HintReason(HintReason.Kind.COLUMN, col) to List(n) { it * n + col } } +
            List(n) { region -> HintReason(HintReason.Kind.REGION, region) to puzzle.regions.indices.filter { puzzle.regions[it] == region } }
        return units
            .mapNotNull { (unit, cells) ->
                val missing = puzzle.queensPerUnit - cells.count { it in candidates.queens }
                val open = cells.filter(candidates::isOpen)
                if (missing > 0 && open.size == missing) QueensDeduction.Place(this, unit, open.first()) else null
            }
            .minWithOrNull(compareBy({ puzzle.regions[it.cell] }, { it.cell }, { it.unit.kind }))
    }
}
