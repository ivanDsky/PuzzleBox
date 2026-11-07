package com.ivandsky.puzzlebox.games.queens.engine.solve

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle

/**
 * The DFS core (zip precedent: ZipSearch): exhaustive solution search with exact counts up
 * to [limit] — uniqueness claims (A13) are exact because the search is exhaustive up to the
 * requested limit. Deterministic: row-by-row backtracking with ascending column subsets,
 * so the same puzzle always yields the same first solution.
 *
 * Like ZipSearch it NEVER calls the verbose rule checks (failures dominate the search):
 * the pipeline predicates appear as primitive form — O(1) column/region counters plus an
 * adjacency check against the previous row only (a queen at (r,c) touches only rows r±1;
 * same-row spacing is enforced while the row's subset is built).
 *
 * Parameterized (A9): each row contributes exactly [QueensPuzzle.queensPerUnit] queens —
 * N=1 is the single-queen special case of the same loop, never a separate code path.
 *
 * Budgeted (ticket 04, spec A7): [bounded] polls its [Budget] once before the search and
 * then every [POLL_INTERVAL] column candidates; once exhausted it unwinds and answers
 * [QueensSearchOutcome.Exhausted]. [solutions] and [search] run it with [Budget.Unlimited],
 * so daily play keeps its unbounded, bit-identical search.
 */
object QueensSearch {

    /** Up to [limit] distinct solutions in deterministic search order (repair loops, tests). */
    fun solutions(puzzle: QueensPuzzle, limit: Int): List<List<Cell>> =
        (bounded(puzzle, limit, Budget.Unlimited) as QueensSearchOutcome.Done).solutions

    /** [solutions], stopped with [QueensSearchOutcome.Exhausted] once [budget] is spent. */
    fun bounded(puzzle: QueensPuzzle, limit: Int, budget: Budget): QueensSearchOutcome {
        val n = puzzle.size
        val q = puzzle.queensPerUnit
        if (budget.exhausted()) return QueensSearchOutcome.Exhausted
        if (limit <= 0) return QueensSearchOutcome.Done(emptyList())

        val regionOf = puzzle.regions.toIntArray()
        val colCount = IntArray(n)
        val regionCount = IntArray(n)
        val queens = ArrayList<Int>(n * q)
        val found = ArrayList<List<Int>>(limit)
        var candidates = 0
        var exhausted = false

        fun dfs(row: Int): Boolean {
            if (row == n) {
                found.add(queens.toList())
                return found.size >= limit
            }
            val remainingRows = n - row
            // Combination-style recursion over this row's column subset: ascending columns,
            // mutual spacing >= 2 (same-row adjacency), q picks total.
            fun pick(startCol: Int, chosenInRow: Int): Boolean {
                if (chosenInRow == q) {
                    return dfs(row + 1)
                }
                val colsLeftInRow = q - chosenInRow
                for (c in startCol..n - colsLeftInRow) {
                    if (++candidates % POLL_INTERVAL == 0 && budget.exhausted()) {
                        exhausted = true
                        return true
                    }
                    if (colCount[c] >= q) continue
                    if (colCount[c] + remainingRows < q) continue // cannot reach N anymore
                    val region = regionOf[row * n + c]
                    if (regionCount[region] >= q) continue
                    // NOTE: no region-feasibility prune — one row may contribute SEVERAL
                    // queens to the same region, so "remaining rows" bounds are unsound.
                    // The colCount prune above IS sound (one queen per column per row).
                    // No-touch vs the previous row only: |c - c'| <= 1 with any queen at row-1.
                    if (row > 0 && queens.any { prev -> prev / n == row - 1 && kotlin.math.abs(prev % n - c) <= 1 }) {
                        continue
                    }
                    colCount[c]++
                    regionCount[region]++
                    queens.add(row * n + c)
                    val hitLimit = pick(c + 2, chosenInRow + 1) // c+2: next pick must not touch
                    queens.removeAt(queens.lastIndex)
                    regionCount[region]--
                    colCount[c]--
                    if (hitLimit) return true
                }
                return false
            }
            return pick(0, 0)
        }

        dfs(0)
        if (exhausted) return QueensSearchOutcome.Exhausted
        return QueensSearchOutcome.Done(found.map { list -> list.map { Cell(it) } })
    }

    fun search(puzzle: QueensPuzzle, limit: Int): QueensSolver.Result {
        val solutions = solutions(puzzle, limit)
        return QueensSolver.Result(solutions.size, solutions.firstOrNull())
    }

    /** Column candidates between budget polls: cheap for daily play, well under 1 ms apart. */
    private const val POLL_INTERVAL = 1024
}
