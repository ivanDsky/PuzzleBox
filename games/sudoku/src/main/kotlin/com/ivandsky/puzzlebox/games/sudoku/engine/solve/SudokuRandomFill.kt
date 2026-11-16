package com.ivandsky.puzzlebox.games.sudoku.engine.solve

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import kotlin.random.Random

/**
 * One random full grid that agrees with a board's given digits. It is [SudokuSolver]'s search
 * (row, column and box digit masks, the open cell with the fewest candidates first) with each
 * cell's digits tried in an order drawn from the injected [Random], so two seeds reach two
 * different grids where the givens leave room for more than one.
 *
 * A few digits drawn early can leave the givens no grid in a way the search only finds out
 * after millions of nodes (about one 9×9 draft in a few hundred, SudokuCompleterMeasurement).
 * So a draw gets a number of nodes; when they are used up the search starts over with fresh
 * draws and twice the nodes, which still ends on givens that have no grid at all.
 */
internal object SudokuRandomFill {
    /** Search nodes of the first draw; a fill that goes well takes about one per open cell. */
    const val FIRST_DRAW_NODES: Int = 1_000

    /**
     * Row-major digits of a full grid holding every nonzero digit of [givens]; null when no
     * grid does (clashing givens included) or [budget] ran out first. The budget is polled at
     * every search node.
     */
    fun fill(geometry: SudokuGeometry, givens: List<Int>, random: Random, budget: Budget): List<Int>? {
        require(givens.size == geometry.cellCount) { "expected ${geometry.cellCount} givens, got ${givens.size}" }
        return RandomFillSearch(geometry, random, budget).run(givens)
    }
}

/** One fill's mutable state; arrays are sized up front so the recursion allocates nothing. */
private class RandomFillSearch(geometry: SudokuGeometry, private val random: Random, private val budget: Budget) {

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

    /** Set when the recursion has to unwind: the budget is spent or the draw's nodes are used up. */
    private var stopped = false

    /** Set once the budget is spent; it is not polled again. */
    private var spent = false
    private var nodesLeft = 0L

    fun run(givens: List<Int>): List<Int>? {
        for (idx in givens.indices) {
            val digit = givens[idx]
            require(digit in 0..size) { "digit $digit at $idx outside 0..$size" }
            if (digit == 0) {
                open[openCount++] = idx
            } else {
                val bit = 1 shl (digit - 1)
                if (usedAt(idx) and bit != 0) return null
                place(idx, bit)
            }
        }
        var nodes = SudokuRandomFill.FIRST_DRAW_NODES.toLong()
        while (true) {
            nodesLeft = nodes
            stopped = false
            if (search(0)) return grid.toList()
            // A search that ran to its end with no grid has shown there is none.
            if (spent || !stopped) return null
            nodes *= 2
        }
    }

    /** True once every open cell holds a digit; the grid is left filled then. */
    private fun search(depth: Int): Boolean {
        if (budget.exhausted()) {
            spent = true
            stopped = true
            return false
        }
        if (nodesLeft-- == 0L) {
            stopped = true
            return false
        }
        if (depth == openCount) return true
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
        if (bestCount == 0) return false
        val idx = open[bestSlot]
        open[bestSlot] = open[depth]
        open[depth] = idx
        var remaining = bestCandidates
        while (remaining != 0 && !stopped) {
            val bit = drawn(remaining)
            remaining = remaining xor bit
            place(idx, bit)
            if (search(depth + 1)) return true
            unplace(idx, bit)
        }
        return false
    }

    /** One set bit of [mask], each equally likely. */
    private fun drawn(mask: Int): Int {
        var rest = mask
        repeat(random.nextInt(Integer.bitCount(mask))) { rest = rest and (rest - 1) }
        return rest and -rest
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
