package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One drag stroke over the trace [path], as a pure state machine: finger samples in, [DragEvent]s
 * out. [DragTraceBoard] feeds it pointer samples; tests feed it synthetic fingers.
 *
 * Every move is walked in [DragTraceCatch.SubStepCells] sub-steps, so a fast swipe skips no cell,
 * and a cell takes effect when the finger first comes into its catch shape ([DragTraceCatch]),
 * not its bare rect: a finger that grazes a cell's corner on its way to another never touches it.
 *
 * - The tail again: nothing. Any earlier traced cell: the trace cuts back to it ([DragEvent.Retract]),
 *   so drawing back over the line undoes it one cell at a time, and touching mid-line picks it up there.
 * - A cell the hooks accept: extends. A diagonal neighbour of the tail the hooks refuse, a 4-way
 *   game's corner cut: extends through whichever side cell the finger passed closer to, when
 *   the hooks accept both steps.
 * - Anything else: [DragEvent.Refused], at most once per [moveTo]; the trace never stops following the
 *   finger because of it.
 */
internal class DragTraceStroke(
    private val geometry: GridGeometry,
    private val hooks: DragTraceHooks,
    path: List<GridCell>,
    down: Offset,
) {
    /** The trace as the stroke has drawn it so far, committed path included. */
    var path: List<GridCell> = path.toList()
        private set

    private var at: Offset = inCells(down)

    /** The cell the finger last came into: a cell is acted on when entered, never while the finger rests in it. */
    private var visited: GridCell? = null

    /** The finger since the trace last changed, in cell units: which side cell a corner cut passed. */
    private val trail = ArrayDeque<Offset>()

    private var events = mutableListOf<DragEvent>()
    private var refused = false

    /** The stroke starts where the finger landed (empty path + start cell, or the tail), wherever in the cell. */
    fun begin(): List<DragEvent> = collect {
        enter(cellOf(at))
        trail.clear()
    }

    /** Walks the finger over [samples] (oldest first), handing the cells it took. */
    fun moveTo(samples: List<Offset>): List<DragEvent> = collect {
        for (sample in samples) walkTo(inCells(sample))
    }

    private fun collect(block: () -> Unit): List<DragEvent> {
        events = mutableListOf()
        refused = false
        block()
        return events
    }

    private fun inCells(position: Offset) = Offset(
        (position.x / geometry.cellWidth).coerceIn(0f, geometry.cols - EDGE_INSET),
        (position.y / geometry.cellHeight).coerceIn(0f, geometry.rows - EDGE_INSET),
    )

    private fun cellOf(point: Offset) = GridCell(floor(point.y).toInt(), floor(point.x).toInt())

    private fun walkTo(to: Offset) {
        val from = at
        val steps = ceil((to - from).getDistance() / DragTraceCatch.SubStepCells).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val point = from + (to - from) * (i.toFloat() / steps)
            record(point)
            val cell = cellOf(point)
            if (cell != visited && DragTraceCatch.catches(point.x - cell.col - 0.5f, point.y - cell.row - 0.5f, reachOf(cell))) {
                enter(cell)
            }
        }
        at = to
    }

    /** An earlier traced cell is caught across its whole width, so backing up is quick; a new one only deep in, so a cell just left is not taken straight back. */
    private fun reachOf(cell: GridCell): Float =
        if (cell in path) DragTraceCatch.RetractReach else DragTraceCatch.ExtendReach

    private fun record(point: Offset) {
        if (trail.size == TRAIL_CAPACITY) trail.removeFirst()
        trail.addLast(point)
    }

    private fun enter(cell: GridCell) {
        visited = cell
        val index = path.indexOf(cell)
        when {
            index >= 0 -> if (index < path.lastIndex) {
                path = path.take(index + 1)
                trail.clear()
                emit(DragEvent.Retract(index))
            }
            hooks.canExtend(path, cell) -> extend(cell)
            else -> {
                val bridge = bridgeTo(cell)
                if (bridge != null) {
                    extend(bridge)
                    extend(cell)
                } else if (!refused) {
                    refused = true
                    emit(DragEvent.Refused(cell))
                }
            }
        }
    }

    private fun extend(cell: GridCell) {
        path = path + cell
        trail.clear()
        emit(DragEvent.Extend(cell))
    }

    private fun emit(event: DragEvent) {
        events += event
    }

    /**
     * The side cell a 4-way trace takes to reach the diagonal [target] of its tail: of the two cells
     * next to both, the one the hooks accept the step through, and when both are, the one the finger
     * passed closer to (a tie goes to the axis the finger travelled further along).
     */
    private fun bridgeTo(target: GridCell): GridCell? {
        val tail = path.lastOrNull() ?: return null
        if (abs(target.row - tail.row) != 1 || abs(target.col - tail.col) != 1) return null
        val horizontal = GridCell(tail.row, target.col)
        val vertical = GridCell(target.row, tail.col)
        val open = listOf(horizontal, vertical).filter { hooks.canExtend(path, it) && hooks.canExtend(path + it, target) }
        if (open.size < 2) return open.firstOrNull()
        val nearH = closestApproach(horizontal)
        val nearV = closestApproach(vertical)
        if (abs(nearH - nearV) > TIE) return if (nearH < nearV) horizontal else vertical
        val travelled = (trail.lastOrNull() ?: at) - Offset(tail.col + 0.5f, tail.row + 0.5f)
        return if (abs(travelled.x) >= abs(travelled.y)) horizontal else vertical
    }

    private fun closestApproach(cell: GridCell): Float {
        val centre = Offset(cell.col + 0.5f, cell.row + 0.5f)
        return trail.minOfOrNull { (it - centre).getDistance() } ?: Float.MAX_VALUE
    }

    private companion object {
        const val EDGE_INSET = 1e-3f
        const val TRAIL_CAPACITY = 64

        /** Two side cells the finger passed this near equally (cell units) are a tie. */
        const val TIE = 0.02f
    }
}

/**
 * Which part of a cell the finger must be in for the cell to count, in cell pitches from its
 * centre: a plus shape. Along either axis the cell is caught out to [ExtendReach] (new cell) or
 * [RetractReach] (earlier traced cell), but only while the other offset is within [Arm]; the four
 * corners are dead, so a finger skirting a cell's corner on its way to the next one never takes it.
 */
internal object DragTraceCatch {
    /** A move is walked in steps no longer than this (cell pitches), so a fast swipe skips no cell. */
    const val SubStepCells: Float = 0.1f

    /** Past this offset on both axes the finger is in a cell's corner, which catches nothing. */
    const val Arm: Float = 0.25f

    /** How far from its centre a new cell is caught; the rest of the half-pitch is a dead rim that keeps a just-retracted cell from re-extending at once. */
    const val ExtendReach: Float = 0.42f

    /** An earlier traced cell is caught right to its edge: undoing is never the hard part. */
    const val RetractReach: Float = 0.5f

    fun catches(u: Float, v: Float, reach: Float): Boolean {
        val x = abs(u)
        val y = abs(v)
        return maxOf(x, y) <= reach && minOf(x, y) <= Arm
    }
}
