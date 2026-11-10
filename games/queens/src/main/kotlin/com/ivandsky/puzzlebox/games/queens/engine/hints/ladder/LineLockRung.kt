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

    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        found(puzzle, candidates).proofs(order(puzzle))

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).first(order(puzzle))

    private fun order(puzzle: QueensPuzzle): Comparator<Found<QueensDeduction.Exclude>> = compareBy(
        { found -> found.deduction.cells.minOf { puzzle.regions[it] } },
        { it.deduction.cells.min() },
        { it.deduction.unit.kind },
    )

    /** Deductions with evidence still unbuilt, so [step] builds none for the losing candidates. */
    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): List<Found<QueensDeduction.Exclude>> {
        val regions = puzzle.regionCells

        val found = ArrayList<Found<QueensDeduction.Exclude>>()
        for (kind in listOf(HintReason.Kind.ROW, HintReason.Kind.COLUMN)) {
            val lineOf: (Int) -> Int = { QueensUnits.indexOf(puzzle, kind, it) }
            val lines = QueensUnits.cells(puzzle, kind)
            fun lock(line: Int, cells: Set<Int>, evidence: List<Int>) {
                if (cells.isNotEmpty()) found += Found(QueensDeduction.Exclude(this, HintReason(kind, line, this), cells)) { evidence.toSet() }
            }
            // A region locked into one line closes the rest of that line …
            regions.forEachIndexed { region, cells ->
                val open = cells.filter(candidates::isOpen)
                val line = open.map(lineOf).distinct().singleOrNull() ?: return@forEachIndexed
                val need = candidates.missing(puzzle, cells)
                if (need > 0 && open.size > need && candidates.missing(puzzle, lines[line]) == need) {
                    lock(line, lines[line].filter { candidates.isOpen(it) && puzzle.regions[it] != region }.toSet(), open)
                }
            }
            // … and a line locked into one region closes the rest of that region.
            lines.forEachIndexed { line, cells ->
                val open = cells.filter(candidates::isOpen)
                val region = open.map { puzzle.regions[it] }.distinct().singleOrNull() ?: return@forEachIndexed
                val need = candidates.missing(puzzle, cells)
                if (need > 0 && open.size > need && candidates.missing(puzzle, regions[region]) == need) {
                    lock(line, regions[region].filter { candidates.isOpen(it) && lineOf(it) != line }.toSet(), open)
                }
            }
        }
        return found
    }
}
