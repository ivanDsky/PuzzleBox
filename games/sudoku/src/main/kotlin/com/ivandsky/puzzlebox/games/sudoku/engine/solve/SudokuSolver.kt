package com.ivandsky.puzzlebox.games.sudoku.engine.solve

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry

/**
 * Finds solutions of a board by backtracking over row, column and box digit masks.
 * Each step fills the open cell with the fewest candidates (MRV); the search stops once
 * the limit is reached. Givens that already clash count as zero solutions.
 */
object SudokuSolver {

    fun solve(geometry: SudokuGeometry, givens: List<Int>, limit: Int = 2): SolveResult {
        val solutions = (bounded(geometry, givens, limit, Budget.Unlimited) as SudokuSearchOutcome.Done).solutions
        return SolveResult(solutions.size, solutions.firstOrNull())
    }

    /**
     * Up to [limit] distinct solutions in search order, or [SudokuSearchOutcome.Exhausted]
     * once [budget] is spent. The budget is polled at every search node.
     */
    fun bounded(geometry: SudokuGeometry, givens: List<Int>, limit: Int, budget: Budget): SudokuSearchOutcome {
        require(givens.size == geometry.cellCount) { "expected ${geometry.cellCount} givens, got ${givens.size}" }
        require(limit >= 1) { "limit must be at least 1, got $limit" }
        return MaskSearch(geometry, limit, budget).run(givens)
    }
}

/** One solve's mutable state; arrays are sized up front so the recursion allocates only solutions. */
private class MaskSearch(geometry: SudokuGeometry, private val limit: Int, private val budget: Budget) {

    private val size = geometry.size
    private val allDigits = (1 shl size) - 1
    private val rowOfCell = IntArray(geometry.cellCount) { geometry.rowOf(it) }
    private val colOfCell = IntArray(geometry.cellCount) { geometry.colOf(it) }
    private val boxOfCell = IntArray(geometry.cellCount) { geometry.boxOf(it) }
    private val rowUsed = IntArray(size)
    private val colUsed = IntArray(size)
    private val boxUsed = IntArray(size)
    private val grid = IntArray(geometry.cellCount)

    // open[0 until depth] are filled; open[depth until openCount] still need a digit.
    private val open = IntArray(geometry.cellCount)
    private var openCount = 0

    private val solutions = ArrayList<List<Int>>()
    private var exhausted = false

    fun run(givens: List<Int>): SudokuSearchOutcome {
        for (idx in givens.indices) {
            val digit = givens[idx]
            require(digit in 0..size) { "digit $digit at $idx outside 0..$size" }
            if (digit == 0) {
                open[openCount++] = idx
            } else {
                val bit = 1 shl (digit - 1)
                if (usedAt(idx) and bit != 0) return SudokuSearchOutcome.Done(emptyList())
                place(idx, bit)
            }
        }
        search(0)
        return if (exhausted) SudokuSearchOutcome.Exhausted else SudokuSearchOutcome.Done(solutions)
    }

    private fun search(depth: Int) {
        if (budget.exhausted()) {
            exhausted = true
            return
        }
        if (depth == openCount) {
            solutions.add(grid.toList())
            return
        }
        var bestSlot = -1
        var bestCandidates = 0
        var bestCount = Int.MAX_VALUE
        for (slot in depth until openCount) {
            val candidates = allDigits and usedAt(open[slot]).inv()
            val candidateCount = Integer.bitCount(candidates)
            if (candidateCount < bestCount) {
                bestSlot = slot
                bestCandidates = candidates
                bestCount = candidateCount
                if (candidateCount <= 1) break
            }
        }
        if (bestCount == 0) return
        val idx = open[bestSlot]
        open[bestSlot] = open[depth]
        open[depth] = idx
        var remaining = bestCandidates
        while (remaining != 0 && !exhausted && solutions.size < limit) {
            val bit = remaining and -remaining
            remaining = remaining xor bit
            place(idx, bit)
            search(depth + 1)
            unplace(idx, bit)
        }
    }

    private fun usedAt(idx: Int): Int =
        rowUsed[rowOfCell[idx]] or colUsed[colOfCell[idx]] or boxUsed[boxOfCell[idx]]

    private fun place(idx: Int, bit: Int) {
        rowUsed[rowOfCell[idx]] = rowUsed[rowOfCell[idx]] or bit
        colUsed[colOfCell[idx]] = colUsed[colOfCell[idx]] or bit
        boxUsed[boxOfCell[idx]] = boxUsed[boxOfCell[idx]] or bit
        grid[idx] = Integer.numberOfTrailingZeros(bit) + 1
    }

    private fun unplace(idx: Int, bit: Int) {
        rowUsed[rowOfCell[idx]] = rowUsed[rowOfCell[idx]] and bit.inv()
        colUsed[colOfCell[idx]] = colUsed[colOfCell[idx]] and bit.inv()
        boxUsed[boxOfCell[idx]] = boxUsed[boxOfCell[idx]] and bit.inv()
        grid[idx] = 0
    }
}
