package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftCommand
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One drag on an end of the path at [index] of [base]: the path, anchored at its other end,
 * follows the cell under the finger, never the trace walk's joins (a walk that took a held cell
 * for joined reroutes, and its pops and pushes are not the finger's). The finger onto the cell
 * before the [grabbed] end pops that end (never below one cell); onto a free cell adjacent to
 * the end, not on the path and held by no other path, pushes it; anywhere else the end stays.
 * A side cell the finger only grazed on its way to the diagonal cell beyond gives way to it.
 */
internal class StrandsEndDrag(val index: Int, private val base: EditorDraft<StrandsDraft>, grabbed: Int) {
    private val board = base.state.draft
    private val original = board.paths[index]
    private val fromStart = grabbed == original.cells.first() && original.cells.size > 1
    private val others = board.paths.filterIndexed { i, _ -> i != index }.flatMapTo(HashSet()) { it.cells }

    /** The working path, the grabbed end last: the live trace while the drag runs. */
    var trace: List<Int> = if (fromStart) original.cells.reversed() else original.cells
        private set

    /** The working path in the path's own reading order. */
    private fun cells() = if (fromStart) trace.reversed() else trace

    /** The finger's last sample, in cell units; the drag starts on the grabbed end's centre. */
    private var last = centreOf(trace.last())

    /** The end, when the drag pushed it as a side cell and the finger only grazed it so far. */
    private var grazed: Int? = null

    /**
     * The finger at [at], in cell units from the grid's top-left. Its way from the last sample
     * is walked in steps of at most [SUB_STEP], so a fast swipe takes every cell it crosses. The
     * draft with the end moved, or null when the end stays where it is.
     */
    fun fingerAt(at: Offset): EditorDraft<StrandsDraft>? {
        val from = last
        val steps = ceil((at - from).getDistance() / SUB_STEP).toInt().coerceAtLeast(1)
        last = at
        var moved = false
        for (i in 1..steps) moved = sample(from + (at - from) * (i.toFloat() / steps)) or moved
        return if (moved) draft() else null
    }

    /**
     * One finger sample: over a cell when inside its centred circle, so a diagonal cutting a
     * corner never touches the side cells. True when the end moved.
     */
    private fun sample(at: Offset): Boolean {
        val row = floor(at.y).toInt()
        val col = floor(at.x).toInt()
        val cell = row * StrandsDraft.COLS + col
        val hit = row in 0 until StrandsDraft.ROWS && col in 0 until StrandsDraft.COLS &&
            (at - centreOf(cell)).getDistance() <= HIT_RADIUS
        val moved = hit && over(cell)
        if (grazed?.let { (at - centreOf(it)).getDistance() <= MEANT_RADIUS } == true) grazed = null
        return moved
    }

    /**
     * The pop/push rule for the finger over [cell]. A side cell pushed while the finger only
     * grazed it gives way to the diagonal cell the finger goes on to: one diagonal, no zig-zag.
     */
    private fun over(cell: Int): Boolean {
        val tail = trace.last()
        val before = trace.getOrNull(trace.size - 2)
        val free = cell !in trace && cell !in others
        val next = when {
            cell == before -> trace.dropLast(1)
            free && tail == grazed && before != null && cutsCorner(before, tail, cell) -> trace.dropLast(1) + cell
            free && coordOf(tail).isAdjacentTo(coordOf(cell)) -> trace + cell
            else -> return false
        }
        grazed = cell.takeIf { next.size > trace.size && coordOf(tail).isSideOf(coordOf(cell)) }
        trace = next
        return true
    }

    /** True when [corner] is a side cell of [from] and [to] its side cell diagonal to [from]. */
    private fun cutsCorner(from: Int, corner: Int, to: Int): Boolean {
        val a = coordOf(from)
        val c = coordOf(to)
        return coordOf(corner).isSideOf(a) && coordOf(corner).isSideOf(c) && a.isAdjacentTo(c) && !a.isSideOf(c)
    }

    /**
     * [base] with the path as dragged, as one [StrandsDraftCommand.EditPath]: cells in the path's
     * own reading order, the popped cells' letters dropped, named when no cell is blank. [base]
     * itself while the path has its cells from before the drag.
     */
    private fun draft(): EditorDraft<StrandsDraft> {
        val cells = cells()
        if (cells == original.cells) return base
        val letters = board.letters.toCharArray()
        original.cells.forEach { if (it !in cells) letters[it] = StrandsDraft.BLANK }
        val spelled = cells.map { letters[it] }.joinToString("")
        val after = StrandsDraftPath(cells, spelled.takeIf { StrandsDraft.BLANK !in it })
        return base.apply(StrandsDraftCommand.EditPath(index, original, after, board.letters, String(letters)))
    }

    private companion object {
        const val CENTRE = 0.5f

        /** The finger is over a cell within this many cell pitches of its centre. */
        const val HIT_RADIUS = 0.45f

        /** The longest stretch of the finger's way taken as one sample, in cell pitches. */
        const val SUB_STEP = 0.15f

        /**
         * Within this many pitches of a pushed side cell's centre the finger meant that cell.
         * A diagonal bulging b toward the side cell passes 0.707 - b from it: up to b ≈ 0.4.
         */
        const val MEANT_RADIUS = 0.3f

        fun coordOf(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)

        fun centreOf(cell: Int) = Offset(cell % StrandsDraft.COLS + CENTRE, cell / StrandsDraft.COLS + CENTRE)

        fun Coord.isSideOf(other: Coord) = abs(row - other.row) + abs(col - other.col) == 1
    }
}

/** The row-major index of [coord] on the editor's board. */
internal fun cellOf(coord: Coord): Int = coord.row * StrandsDraft.COLS + coord.col

/** The letters of the path at [index] up to its first blank, as the word field shows a selected path. */
internal fun StrandsDraft.spelled(index: Int): String =
    paths[index].cells.map { letters[it] }.takeWhile { it != StrandsDraft.BLANK }.joinToString("")
