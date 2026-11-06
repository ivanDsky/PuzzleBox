package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * Second rung (spec A9): a region whose open cells all lie in one row (or column) owes
 * that line its missing queens, so the rest of the line is excluded. The dual: a line whose
 * open cells all lie in one region owes it its queens, so the rest of that region is excluded.
 * Either way the reason names the line. A unit already down to a single is left to
 * [SinglesRung], and the lock holds only when the region and the line miss the same number
 * of queens (always, for N=1).
 *
 * Order (00 §4.3): lowest region of the excluded cells, then lowest excluded cell, then the
 * ROW → COLUMN proof. Every returned step excludes at least one open cell.
 */
object LineLockRung : QueensRung {
    override val id: String = "line-lock"
    override val level: Difficulty.Level = Difficulty.Level.Medium
    override val captionKey: String = "queens.hint.rung.line-lock"

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? {
        val n = puzzle.size
        val regions = List(n) { region -> puzzle.regions.indices.filter { puzzle.regions[it] == region } }
        fun missing(cells: List<Int>): Int = puzzle.queensPerUnit - cells.count { it in candidates.queens }

        val found = ArrayList<QueensDeduction.Exclude>()
        for (kind in listOf(HintReason.Kind.ROW, HintReason.Kind.COLUMN)) {
            val lineOf: (Int) -> Int = if (kind == HintReason.Kind.ROW) { cell -> cell / n } else { cell -> cell % n }
            val lines = List(n) { line -> (0 until n * n).filter { lineOf(it) == line } }
            fun lock(line: Int, cells: Set<Int>) {
                if (cells.isNotEmpty()) found += QueensDeduction.Exclude(this, HintReason(kind, line, this), cells)
            }
            // A region locked into one line closes the rest of that line …
            regions.forEachIndexed { region, cells ->
                val open = cells.filter(candidates::isOpen)
                val line = open.map(lineOf).distinct().singleOrNull() ?: return@forEachIndexed
                val need = missing(cells)
                if (need > 0 && open.size > need && missing(lines[line]) == need) {
                    lock(line, lines[line].filter { candidates.isOpen(it) && puzzle.regions[it] != region }.toSet())
                }
            }
            // … and a line locked into one region closes the rest of that region.
            lines.forEachIndexed { line, cells ->
                val open = cells.filter(candidates::isOpen)
                val region = open.map { puzzle.regions[it] }.distinct().singleOrNull() ?: return@forEachIndexed
                val need = missing(cells)
                if (need > 0 && open.size > need && missing(regions[region]) == need) {
                    lock(line, regions[region].filter { candidates.isOpen(it) && lineOf(it) != line }.toSet())
                }
            }
        }
        return found.minWithOrNull(
            compareBy({ it.cells.minOf { cell -> puzzle.regions[cell] } }, { it.cells.min() }, { it.unit.kind }),
        )
    }
}
