package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict

/** Hand-verifiable boards and builders shared by the solver/generator test suite. */
object QueensTestSupport {

    /** Build a puzzle with region id = [regionOf](row, col); ids must land in 0 until size. */
    fun puzzle(size: Int, queensPerUnit: Int = 1, regionOf: (row: Int, col: Int) -> Int = { r, _ -> r }): QueensPuzzle {
        val regions = List(size * size) { idx -> regionOf(idx / size, idx % size) }
        return QueensPuzzle(size = size, regions = regions, queensPerUnit = queensPerUnit)
    }

    fun cell(row: Int, col: Int, size: Int) = Cell(row * size + col)

    /**
     * Find A placement with exactly [QueensPuzzle.queensPerUnit] queens per row/column/
     * region and no touching, or null. Rows are filled in order; within and between
     * adjacent rows the chosen columns must be pairwise non-neighboring. Deterministic
     * (first solution in column order). Small boards only — test oracle, not the engine
     * solver (queens-03's engine/solve is the real one).
     */
    fun solve(puzzle: QueensPuzzle): Set<Cell>? {
        val n = puzzle.size
        val k = puzzle.queensPerUnit
        val colCount = IntArray(n)
        val regionCount = IntArray(n)
        val placement = ArrayList<List<Int>>(n) // columns chosen per row

        fun rowSolutions(row: Int): Boolean {
            if (row == n) return true
            val prev = placement.lastOrNull() ?: emptyList()
            val chosen = ArrayList<Int>(k)
            fun cols(from: Int): Boolean {
                if (chosen.size == k) {
                    placement.add(chosen.toList())
                    for (c in chosen) {
                        colCount[c]++
                        regionCount[puzzle.regionOf(cell(row, c, n))]++
                    }
                    if (rowSolutions(row + 1)) return true
                    placement.removeAt(placement.lastIndex)
                    for (c in chosen) {
                        colCount[c]--
                        regionCount[puzzle.regionOf(cell(row, c, n))]--
                    }
                    return false
                }
                for (c in from until n) {
                    if (colCount[c] >= k) continue
                    if (regionCount[puzzle.regionOf(cell(row, c, n))] >= k) continue
                    if (chosen.any { kotlin.math.abs(it - c) <= 1 }) continue // within row: no touch
                    if (prev.any { kotlin.math.abs(it - c) <= 1 }) continue // vs previous row
                    chosen.add(c)
                    val ok = cols(c + 2) // next in-row column must differ by ≥2
                    chosen.removeAt(chosen.lastIndex)
                    if (ok) return true
                }
                return false
            }
            return cols(0)
        }

        return if (rowSolutions(0)) {
            placement.flatMapIndexed { r, cols -> cols.map { c -> cell(r, c, n) } }.toSet()
        } else {
            null
        }
    }

    /** Evaluate helper for the rules property suite. */
    fun evaluate(puzzle: QueensPuzzle, queens: Set<Cell>): QueensRulesVerdict =
        QueensRules.evaluate(puzzle, queens)


    /**
     * 4×4, N=1, UNIQUE by construction (independently enumerated, not by the solver):
     * queens at (0,1),(1,3),(2,0),(3,2); the region map grown around them kills the
     * second classic 4-queens solution [2,0,3,1] — its row-0 queen (0,2) lands in
     * region 0, which region also holds... every alternative full placement forces two
     * queens into one region (exhaustive check: exactly 1 solution).
     */
    val n4Regions = listOf(
        0, 0, 0, 0,
        0, 1, 1, 1,
        2, 2, 2, 1,
        2, 2, 3, 1,
    )
    val n4Solution = path(1, 7, 8, 14)

    fun n4Puzzle(): QueensPuzzle = QueensPuzzle(4, n4Regions, 1, n4Solution)

    /**
     * 4×4, N=1, MULTIPLE solutions: regions mirror the columns, so the region rule is
     * redundant with the column rule and both classic 4-queens placements survive —
     * the mutation-falsification board for false uniqueness.
     */
    fun n4OpenPuzzle(): QueensPuzzle =
        QueensPuzzle(4, (0 until 16).map { it % 4 }, 1, null)

    /**
     * 8×8, N=2 (Star Battle shape, A9): two queens per row/column/region, no-touch.
     * Queens at cols [1,3],[5,7],[1,3],[5,7],[0,2],[4,6],[0,2],[4,6] per row; region map
     * grown around the two-queen bands; independently enumerated: exactly 1 solution.
     */
    val n8N2Regions = listOf(
        0, 0, 0, 0, 0, 0, 0, 1,
        0, 2, 2, 0, 1, 1, 1, 1,
        2, 2, 4, 2, 2, 1, 1, 1,
        4, 2, 4, 2, 3, 3, 3, 3,
        4, 4, 4, 2, 3, 3, 3, 3,
        4, 4, 6, 5, 5, 5, 5, 5,
        6, 6, 6, 6, 5, 5, 7, 7,
        6, 6, 6, 7, 7, 7, 7, 7,
    )
    val n8N2Solution = path(1, 3, 13, 15, 17, 19, 29, 31, 32, 34, 44, 46, 48, 50, 60, 62)

    fun n8N2Puzzle(): QueensPuzzle = QueensPuzzle(8, n8N2Regions, 2, n8N2Solution)

    fun variantOf(size: Int, queensPerUnit: Int = 1): QueensVariant =
        QueensVariant(queensVariantId(size), size, queensPerUnit)

    fun path(vararg idx: Int): List<Cell> = idx.map { Cell(it) }
}
