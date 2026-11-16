package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget

/**
 * The answer search of [QueensPinnedGrowth]'s repair loop, which asks for two answers at every
 * step. Exhaustive like the engine's row-by-row
 * [com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch], but it branches on the row,
 * column or region with the fewest open cells and drops a branch once one of them has none, so
 * a step stays cheap at sizes 11 and 12. A completion is still judged by [QueensDraftAnalyzer].
 *
 * A row's open cells are the bits of one Int, so the board is at most 31 wide.
 */
internal class QueensPinnedSearch(private val n: Int, private val budget: Budget) {
    private val open = Array(n + 1) { IntArray(n) }
    private val branch = Array(n) { IntArray(n * n) }
    private val regionInRow = IntArray(n * n)
    private val inColumn = IntArray(n)
    private val inRegion = IntArray(n)
    private val queens = IntArray(n)
    private val found = ArrayList<List<Int>>()
    private var regions = IntArray(0)
    private var limit = 0
    private var nodes = 0
    private var spent = false

    /** Up to [limit] answers of the full board [regions], each as ascending cells; null once the budget is spent. */
    fun answers(regions: IntArray, limit: Int): List<List<Int>>? {
        if (budget.exhausted()) return null
        this.regions = regions
        this.limit = limit
        found.clear()
        spent = false
        regionInRow.fill(0)
        for (cell in regions.indices) regionInRow[regions[cell] * n + cell / n] += 1 shl cell % n
        val all = (1 shl n) - 1
        open[0].fill(all)
        search(depth = 0, rows = all, columns = all, regionsLeft = all)
        return if (spent) null else found.toList()
    }

    /** Places the queens the board still misses; true ends the whole search (limit reached or budget spent). */
    private fun search(depth: Int, rows: Int, columns: Int, regionsLeft: Int): Boolean {
        if (rows == 0) {
            found += queens.toList()
            return found.size >= limit
        }
        spent = spent || (++nodes % POLL_INTERVAL == 0 && budget.exhausted())
        if (spent) return true
        val here = open[depth]
        // The unit with the fewest open cells: a row first, then columns and regions from the counts.
        inColumn.fill(0)
        inRegion.fill(0)
        var fewest = Int.MAX_VALUE
        var rowMask = 0
        var columnMask = 0
        var region = NONE
        for (row in 0 until n) {
            if (rows shr row and 1 == 0) continue
            var cells = here[row]
            val count = cells.countOneBits()
            if (count < fewest) {
                fewest = count
                rowMask = 1 shl row
                columnMask = -1
            }
            while (cells != 0) {
                val column = cells.countTrailingZeroBits()
                cells = cells and cells - 1
                inColumn[column]++
                inRegion[regions[row * n + column]]++
            }
        }
        for (unit in 0 until n) {
            if (columns shr unit and 1 == 1 && inColumn[unit] < fewest) {
                fewest = inColumn[unit]
                rowMask = rows
                columnMask = 1 shl unit
                region = NONE
            }
            if (regionsLeft shr unit and 1 == 1 && inRegion[unit] < fewest) {
                fewest = inRegion[unit]
                rowMask = rows
                columnMask = -1
                region = unit
            }
        }
        if (fewest == 0) return false
        // Exactly one of the unit's open cells holds its queen, so the branches share no answer.
        val cells = branch[depth]
        var count = 0
        for (row in 0 until n) {
            if (rowMask shr row and 1 == 0) continue
            var bits = here[row] and columnMask
            if (region != NONE) bits = bits and regionInRow[region * n + row]
            while (bits != 0) {
                cells[count++] = row * n + bits.countTrailingZeroBits()
                bits = bits and bits - 1
            }
        }
        val next = open[depth + 1]
        for (index in 0 until count) {
            val row = cells[index] / n
            val column = cells[index] % n
            val own = regions[cells[index]]
            for (other in 0 until n) next[other] = here[other] and (1 shl column).inv() and regionInRow[own * n + other].inv()
            val touching = (7 shl column shr 1).inv()
            if (row > 0) next[row - 1] = next[row - 1] and touching
            if (row < n - 1) next[row + 1] = next[row + 1] and touching
            queens[row] = cells[index]
            if (search(depth + 1, rows xor (1 shl row), columns xor (1 shl column), regionsLeft xor (1 shl own))) return true
        }
        return false
    }

    private companion object {
        const val NONE = -1

        /** Search nodes between budget polls. */
        const val POLL_INTERVAL = 256
    }
}
