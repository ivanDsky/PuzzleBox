package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

private typealias LockFound = Pair<QueensDeduction.Exclude, () -> Set<Int>>

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
        found(puzzle, candidates).sortedWith(order(puzzle)).asSequence().map { (deduction, evidence) -> QueensProof(deduction, evidence()) }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).minWithOrNull(order(puzzle))?.first

    private fun order(puzzle: QueensPuzzle): Comparator<LockFound> = compareBy(
        { found -> found.first.cells.minOf { puzzle.regions[it] } },
        { it.first.cells.min() },
        { it.first.unit.kind },
    )

    /** Deductions with evidence still unbuilt, so [step] builds none for the losing candidates. */
    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): List<LockFound> {
        val regions = puzzle.regionCells
        fun missing(cells: List<Int>): Int = puzzle.queensPerUnit - cells.count { it in candidates.queens }

        val found = ArrayList<LockFound>()
        for (kind in listOf(HintReason.Kind.ROW, HintReason.Kind.COLUMN)) {
            val lineOf: (Int) -> Int = { QueensUnits.indexOf(puzzle, kind, it) }
            val lines = QueensUnits.cells(puzzle, kind)
            fun lock(line: Int, cells: Set<Int>, evidence: List<Int>) {
                if (cells.isNotEmpty()) found += QueensDeduction.Exclude(this, HintReason(kind, line, this), cells) to { evidence.toSet() }
            }
            // A region locked into one line closes the rest of that line …
            regions.forEachIndexed { region, cells ->
                val open = cells.filter(candidates::isOpen)
                val line = open.map(lineOf).distinct().singleOrNull() ?: return@forEachIndexed
                val need = missing(cells)
                if (need > 0 && open.size > need && missing(lines[line]) == need) {
                    lock(line, lines[line].filter { candidates.isOpen(it) && puzzle.regions[it] != region }.toSet(), open)
                }
            }
            // … and a line locked into one region closes the rest of that region.
            lines.forEachIndexed { line, cells ->
                val open = cells.filter(candidates::isOpen)
                val region = open.map { puzzle.regions[it] }.distinct().singleOrNull() ?: return@forEachIndexed
                val need = missing(cells)
                if (need > 0 && open.size > need && missing(regions[region]) == need) {
                    lock(line, regions[region].filter { candidates.isOpen(it) && lineOf(it) != line }.toSet(), open)
                }
            }
        }
        return found
    }
}
