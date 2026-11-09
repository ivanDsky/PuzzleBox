package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoMarkerRule.violates

/**
 * The [TangoUniqueness] search state: a mutable grid seeded with [puzzle]'s givens, filled by
 * [place] and the forced moves [propagate] and [probe] derive, and rolled back by trail
 * ([mark]/[undoTo]), never by copying.
 *
 * The three rule classes are re-implemented over arrays so a search node allocates nothing:
 * no run of three ([TangoTripleRule]), no line over n/2 of a value ([TangoCountRule]), no broken
 * `=`/`×` edge ([TangoMarkerRule]); `TangoPropagationRulesTest` pins them against those classes.
 * A move is forced only when its other value breaks a rule, so the full grids reached are
 * exactly the solutions. Givens that already break a rule, or `=`/`×` markers and givens that
 * cannot agree on a value (an odd parity cycle), leave no solution: [propagate] then fails at once.
 */
internal class TangoPropagation(puzzle: TangoPuzzle) {
    private val n = puzzle.n
    private val half = n / 2

    /** Row-major cells; null = empty. Givens are set up front and never on the trail. */
    val grid: Array<TangoCell?> = Array(puzzle.cellCount) { idx -> puzzle.givens[idx] }

    /** The marker on the edge to the right of / below a cell, first marker per edge as [TangoPuzzle.markerBetween]. */
    private val right = arrayOfNulls<TangoMarkerKind>(puzzle.cellCount)
    private val down = arrayOfNulls<TangoMarkerKind>(puzzle.cellCount)

    /** How many marker edges touch each cell: the static part of a cell's constraint degree. */
    private val markerDegree = IntArray(puzzle.cellCount)

    /** Per line (rows 0 until n, then columns) and value ordinal: how many cells hold it. */
    private val counts = IntArray(4 * n)

    private val trail = IntArray(puzzle.cellCount)
    private var trailSize = 0

    /** Filled cells whose neighborhood [propagate] has not yet read. */
    private val queue = IntArray(puzzle.cellCount)
    private var queueSize = 0

    /** True when the givens and markers alone rule out every solution. */
    private val unsatisfiable: Boolean

    init {
        for (marker in puzzle.markers.asReversed()) {
            when (marker.side) {
                TangoEdgeSide.RIGHT -> right[marker.cell] = marker.kind
                TangoEdgeSide.DOWN -> down[marker.cell] = marker.kind
            }
        }
        for (idx in grid.indices) {
            if (right[idx] != null) {
                markerDegree[idx]++
                markerDegree[idx + 1]++
            }
            if (down[idx] != null) {
                markerDegree[idx]++
                markerDegree[idx + n]++
            }
        }
        for ((idx, cell) in puzzle.givens) {
            count(idx, cell, +1)
            queue[queueSize++] = idx
        }
        unsatisfiable = puzzle.givens.keys.any { idx -> !consistentAt(idx) } || oddParityCycle(puzzle)
    }

    /**
     * Union-find with parity over the `=` (same) and `×` (differ) edges, each given tied to a
     * SUN node: true when two paths between the same cells disagree on parity, which no
     * assignment satisfies. Runs once, before the search.
     */
    private fun oddParityCycle(puzzle: TangoPuzzle): Boolean {
        val sun = puzzle.cellCount
        val parent = IntArray(sun + 1) { it }
        val parity = IntArray(sun + 1)
        fun find(x: Int): Int {
            val p = parent[x]
            if (p == x) return x
            val root = find(p)
            parity[x] = parity[x] xor parity[p]
            parent[x] = root
            return root
        }
        fun joins(a: Int, b: Int, differ: Int): Boolean {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return parity[a] xor parity[b] == differ
            parent[ra] = rb
            parity[ra] = parity[a] xor parity[b] xor differ
            return true
        }
        fun differ(kind: TangoMarkerKind): Int = if (kind == TangoMarkerKind.SAME) 0 else 1
        for (idx in grid.indices) {
            if (right[idx]?.let { joins(idx, idx + 1, differ(it)) } == false) return true
            if (down[idx]?.let { joins(idx, idx + n, differ(it)) } == false) return true
        }
        return puzzle.givens.any { (idx, cell) -> !joins(idx, sun, cell.ordinal) }
    }

    /** The trail position to roll back to with [undoTo]. */
    fun mark(): Int = trailSize

    /** Empties every cell placed since [mark] and drops any pending propagation. */
    fun undoTo(mark: Int) {
        while (trailSize > mark) {
            val idx = trail[--trailSize]
            count(idx, grid[idx]!!, -1)
            grid[idx] = null
        }
        queueSize = 0
    }

    /** Fills the empty cell [idx] with [value]; false when a constraint through [idx] breaks. */
    fun place(idx: Int, value: TangoCell): Boolean {
        grid[idx] = value
        trail[trailSize++] = idx
        count(idx, value, +1)
        queue[queueSize++] = idx
        return consistentAt(idx)
    }

    /**
     * Fills every forced cell until none is left: the gap in `XX_`, `_XX` or `X_X`, the rest of
     * a line holding n/2 of one value, the far end of an `=`/`×` edge. False on a contradiction.
     */
    fun propagate(): Boolean {
        if (unsatisfiable) return false
        while (queueSize > 0) {
            val idx = queue[--queueSize]
            if (!forceAround(idx)) {
                queueSize = 0
                return false
            }
        }
        return true
    }

    /**
     * Failed-literal probing to a fixpoint: a value that [propagate] refutes forces the other
     * one. False when both values of some empty cell fail. Call on a propagated grid; afterwards
     * every empty cell has two legal values.
     */
    fun probe(): Boolean {
        var changed = true
        while (changed) {
            changed = false
            for (idx in grid.indices) {
                if (grid[idx] != null) continue
                val forced = when {
                    !holds(idx, TangoCell.SUN) -> TangoCell.MOON
                    !holds(idx, TangoCell.MOON) -> TangoCell.SUN
                    else -> continue
                }
                if (!place(idx, forced) || !propagate()) return false
                changed = true
            }
        }
        return true
    }

    /**
     * The empty cell to branch on, -1 when the grid is full. After [probe] every empty cell has
     * two legal values, so the pick is the highest constraint degree (marker edges on the cell
     * plus filled cells in its row and column), row-major on ties.
     */
    fun mostConstrained(): Int {
        var best = -1
        var bestDegree = -1
        for (idx in grid.indices) {
            if (grid[idx] != null) continue
            val row = idx / n
            val col = n + idx % n
            val degree = markerDegree[idx] + counts[2 * row] + counts[2 * row + 1] + counts[2 * col] + counts[2 * col + 1]
            if (degree > bestDegree) {
                best = idx
                bestDegree = degree
            }
        }
        return best
    }

    /** True when [value] at the empty cell [idx] survives propagation; the grid is left as found. */
    private fun holds(idx: Int, value: TangoCell): Boolean {
        val mark = mark()
        val ok = place(idx, value) && propagate()
        undoTo(mark)
        return ok
    }

    /** Fills what the cell [idx] forces through its two lines and its marker edges. */
    private fun forceAround(idx: Int): Boolean {
        val value = grid[idx] ?: return true
        val row = idx / n
        val col = idx % n
        for (start in maxOf(0, col - 2)..minOf(col, n - 3)) {
            if (!forceTriple(row * n + start, 1)) return false
        }
        for (start in maxOf(0, row - 2)..minOf(row, n - 3)) {
            if (!forceTriple(start * n + col, n)) return false
        }
        if (col < n - 1 && !forceEdge(idx + 1, value, right[idx])) return false
        if (col > 0 && !forceEdge(idx - 1, value, right[idx - 1])) return false
        if (row < n - 1 && !forceEdge(idx + n, value, down[idx])) return false
        if (row > 0 && !forceEdge(idx - n, value, down[idx - n])) return false
        return forceLine(row * n, 1, row, value) && forceLine(col, n, n + col, value)
    }

    /** The window of three cells from [first], [step] apart: two equal and one empty forces the gap. */
    private fun forceTriple(first: Int, step: Int): Boolean {
        val a = grid[first]
        val b = grid[first + step]
        val c = grid[first + 2 * step]
        return when {
            a != null && a == b && c == null -> place(first + 2 * step, a.other())
            b != null && b == c && a == null -> place(first, b.other())
            a != null && a == c && b == null -> place(first + step, a.other())
            else -> true
        }
    }

    private fun forceEdge(neighbor: Int, value: TangoCell, kind: TangoMarkerKind?): Boolean {
        if (kind == null || grid[neighbor] != null) return true
        return place(neighbor, if (kind == TangoMarkerKind.SAME) value else value.other())
    }

    /** Once [line] holds n/2 of [value], its empty cells take the other value. */
    private fun forceLine(first: Int, step: Int, line: Int, value: TangoCell): Boolean {
        if (counts[2 * line + value.ordinal] < half) return true
        for (k in 0 until n) {
            val idx = first + k * step
            if (grid[idx] == null && !place(idx, value.other())) return false
        }
        return true
    }

    /** No run of three, no line over n/2 of either value, and no broken marker edge through [idx]. */
    private fun consistentAt(idx: Int): Boolean {
        val row = idx / n
        val col = idx % n
        if (overHalf(row) || overHalf(n + col)) return false
        val value = grid[idx]
        for (start in maxOf(0, col - 2)..minOf(col, n - 3)) {
            val first = row * n + start
            if (grid[first] == value && grid[first + 1] == value && grid[first + 2] == value) return false
        }
        for (start in maxOf(0, row - 2)..minOf(row, n - 3)) {
            val first = start * n + col
            if (grid[first] == value && grid[first + n] == value && grid[first + 2 * n] == value) return false
        }
        return !(col < n - 1 && right[idx]?.violates(value, grid[idx + 1]) == true) &&
            !(col > 0 && right[idx - 1]?.violates(grid[idx - 1], value) == true) &&
            !(row < n - 1 && down[idx]?.violates(value, grid[idx + n]) == true) &&
            !(row > 0 && down[idx - n]?.violates(grid[idx - n], value) == true)
    }

    private fun count(idx: Int, value: TangoCell, delta: Int) {
        counts[2 * (idx / n) + value.ordinal] += delta
        counts[2 * (n + idx % n) + value.ordinal] += delta
    }

    private fun overHalf(line: Int): Boolean = counts[2 * line] > half || counts[2 * line + 1] > half

    private fun TangoCell.other(): TangoCell = if (this == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN
}
