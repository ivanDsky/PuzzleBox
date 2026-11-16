package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoLines

/**
 * The hint ladder of [com.ivandsky.puzzlebox.games.tango.engine.TangoRules.hint] walked on plain
 * arrays, for the scored grader and the completer that aims for a level (93): both walk hundreds
 * of boards, and the rule objects allocate a list per rule per step.
 *
 * Each step looks at every move the board offers and places the one the hint ladder would: the
 * first marker with one end filled, else the first three-in-a-row window, else the first empty
 * cell of a line holding half of one value, else the first cell every legal fill of a line agrees
 * on, rows before columns throughout. `TangoSolveWalkTest` holds it to the rule objects on the real
 * dailies and on generated boards of every size.
 *
 * A step is narrow when one cell only is on offer and it is not the last empty cell: the solver
 * has to find that cell. On offer are the cells the three plain rules force (marker,
 * three-in-a-row, line count); when they force none, the cells line fills force.
 *
 * One walker serves every board of a size and marker set; [tally] is not thread-safe.
 */
internal class TangoSolveWalk(private val n: Int, markers: List<TangoMarker>) {
    private val half = n / 2
    private val lines: Array<IntArray> = TangoLines.all(n)
    private val markerCell = IntArray(markers.size) { markers[it].cell }
    private val markerNeighbor = IntArray(markers.size) { neighbor(markers[it]) }
    private val markerSame = BooleanArray(markers.size) { markers[it].kind == TangoMarkerKind.SAME }

    /** Per line, every legal full line as a bit mask: bit j set means position j holds a sun. */
    private val fills: Array<IntArray> = legalFills(markers)

    private val grid = IntArray(n * n)
    private val offered = IntArray(n * n)
    private var stamp = 0
    private var moves = 0
    private var pick = NONE
    private var pickValue = EMPTY

    /**
     * Walks the ladder from [givens] until the board is full or no rung has a move. Polls [budget]
     * before each step; null once it is spent.
     */
    fun tally(givens: Map<Int, TangoCell>, budget: Budget): TangoSolveTally? {
        grid.fill(EMPTY)
        for ((cell, value) in givens) grid[cell] = if (value == TangoCell.SUN) SUN else MOON
        val steps = IntArray(RUNGS)
        var narrow = 0
        var left = grid.size - givens.size
        while (left > 0) {
            if (budget.exhausted()) return null
            stamp++
            moves = 0
            pick = NONE
            var rung = NONE
            offerMarkers()
            if (pick != NONE) rung = MARKER
            offerTriples()
            if (rung == NONE && pick != NONE) rung = TRIPLE
            offerCounts()
            if (rung == NONE && pick != NONE) rung = LINE_FULL
            if (rung == NONE) {
                offerFills()
                if (pick == NONE) break
                rung = LINE_FILLS
            }
            grid[pick] = pickValue
            steps[rung]++
            if (moves == 1 && left > 1) narrow++
            left--
        }
        return TangoSolveTally(steps[MARKER], steps[TRIPLE], steps[LINE_FULL], steps[LINE_FILLS], narrow, left)
    }

    /** The cells the last [tally] left empty, in board order: where the ladder got stuck. */
    fun stuck(): List<Int> = grid.indices.filter { grid[it] == EMPTY }

    /** Counts [cell] as a move on offer; the first cell offered in a step is the one placed. */
    private fun offer(cell: Int, value: Int) {
        if (pick == NONE) {
            pick = cell
            pickValue = value
        }
        if (offered[cell] != stamp) {
            offered[cell] = stamp
            moves++
        }
    }

    private fun offerMarkers() {
        for (m in markerCell.indices) {
            val a = grid[markerCell[m]]
            val b = grid[markerNeighbor[m]]
            if ((a == EMPTY) == (b == EMPTY)) continue
            val known = a + b
            offer(if (a == EMPTY) markerCell[m] else markerNeighbor[m], if (markerSame[m]) known else SUN + MOON - known)
        }
    }

    private fun offerTriples() {
        for (line in lines) {
            for (i in 0 until n - 2) {
                val a = grid[line[i]]
                val b = grid[line[i + 1]]
                val c = grid[line[i + 2]]
                if (a != EMPTY && a == b && c == EMPTY) offer(line[i + 2], SUN + MOON - a)
                if (b != EMPTY && b == c && a == EMPTY) offer(line[i], SUN + MOON - b)
                if (a != EMPTY && a == c && b == EMPTY) offer(line[i + 1], SUN + MOON - a)
            }
        }
    }

    private fun offerCounts() {
        for (line in lines) {
            var suns = 0
            var moons = 0
            for (cell in line) {
                when (grid[cell]) {
                    SUN -> suns++
                    MOON -> moons++
                }
            }
            if (suns == half) for (cell in line) if (grid[cell] == EMPTY) offer(cell, MOON)
            if (moons == half) for (cell in line) if (grid[cell] == EMPTY) offer(cell, SUN)
        }
    }

    private fun offerFills() {
        for (l in lines.indices) {
            val line = lines[l]
            var suns = 0
            var moons = 0
            for (j in 0 until n) {
                when (grid[line[j]]) {
                    SUN -> suns = suns or (1 shl j)
                    MOON -> moons = moons or (1 shl j)
                }
            }
            val open = ((1 shl n) - 1) and (suns or moons).inv()
            if (open == 0) continue
            var always = open
            var ever = 0
            var legal = false
            for (fill in fills[l]) {
                if (fill and suns != suns || fill and moons != 0) continue
                always = always and fill
                ever = ever or fill
                legal = true
            }
            if (!legal) continue
            val never = open and ever.inv()
            for (j in 0 until n) {
                val bit = 1 shl j
                if (always and bit != 0) offer(line[j], SUN) else if (never and bit != 0) offer(line[j], MOON)
            }
        }
    }

    /** Balanced lines without three in a row, kept per line when they also hold its markers. */
    private fun legalFills(markers: List<TangoMarker>): Array<IntArray> {
        val balanced = (0 until (1 shl n)).filter { mask ->
            Integer.bitCount(mask) == half && (0 until n - 2).none { j -> (mask shr j and 0b111).let { it == 0 || it == 0b111 } }
        }
        val kinds = HashMap<Long, TangoMarkerKind>()
        for (marker in markers) kinds.putIfAbsent(edge(marker.cell, neighbor(marker)), marker.kind)
        return Array(lines.size) { l ->
            val line = lines[l]
            balanced.filter { mask ->
                (0 until n - 1).all { j ->
                    val kind = kinds[edge(line[j], line[j + 1])]
                    kind == null || (kind == TangoMarkerKind.SAME) == ((mask shr j and 1) == (mask shr (j + 1) and 1))
                }
            }.toIntArray()
        }
    }

    private fun neighbor(marker: TangoMarker): Int = if (marker.side == TangoEdgeSide.RIGHT) marker.cell + 1 else marker.cell + n

    private fun edge(cell: Int, neighbor: Int): Long = cell.toLong() shl Int.SIZE_BITS or neighbor.toLong()

    private companion object {
        const val EMPTY = 0
        const val SUN = 1
        const val MOON = 2
        const val NONE = -1
        const val MARKER = 0
        const val TRIPLE = 1
        const val LINE_FULL = 2
        const val LINE_FILLS = 3
        const val RUNGS = 4
    }
}
