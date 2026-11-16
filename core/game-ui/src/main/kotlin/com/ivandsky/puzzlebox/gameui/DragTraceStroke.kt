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
 * - The tail again: nothing. The cell before the tail: the trace steps back one cell
 *   ([DragEvent.Retract]) — the only way onto the drawn line, so backing along it undoes it
 *   one cell at a time. Any other traced cell is a wall ([DragEvent.Refused]): never cut
 *   back to, never crossed, never picked up.
 * - A cell the hooks accept: extends. A diagonal neighbour of the tail the hooks refuse, a 4-way
 *   game's corner cut: extends through whichever side cell the finger passed closer to, when
 *   the hooks accept both steps.
 * - A cell further from the tail, caught after the finger crossed the cells between through their
 *   dead zones only: the trace fills in every cell the finger crossed on its way from the tail, in
 *   order, up to the first step the hooks refuse.
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

    /**
     * The cells the finger crossed since it was last on the tail, the tail first and none twice: the
     * way a caught cell out of the tail's reach is filled in along. Empty while the finger has not
     * come from the tail.
     */
    private val route = ArrayList<GridCell>()

    private var events = mutableListOf<DragEvent>()
    private var refused = false

    /** The stroke starts where the finger landed (empty path + start cell, or the tail), wherever in the cell. */
    fun begin(): List<DragEvent> = collect {
        val cell = cellOf(at)
        cross(cell)
        enter(cell)
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
            cross(cell)
            if (cell != visited && DragTraceCatch.catches(point.x - cell.col - 0.5f, point.y - cell.row - 0.5f, reachOf(cell))) {
                enter(cell)
            }
        }
        at = to
    }

    /** The cell before the tail is caught right to its edge: stepping back is never the hard part. */
    private fun reachOf(cell: GridCell): Float =
        if (cell == path.getOrNull(path.lastIndex - 1)) DragTraceCatch.RetractReach else DragTraceCatch.ExtendReach

    private fun record(point: Offset) {
        if (trail.size == TRAIL_CAPACITY) trail.removeFirst()
        trail.addLast(point)
    }

    /** A cell crossed again cuts the loop the finger made since: jitter over a boundary is no detour to draw. */
    private fun cross(cell: GridCell) {
        val seen = route.indexOf(cell)
        when {
            seen >= 0 -> route.subList(seen + 1, route.size).clear()
            route.isNotEmpty() || cell == path.lastOrNull() -> route += cell
        }
    }

    private fun enter(cell: GridCell) {
        visited = cell
        when {
            cell == path.getOrNull(path.lastIndex - 1) -> {
                path = path.dropLast(1)
                redrawn()
                emit(DragEvent.Retract(path.lastIndex))
            }
            cell == path.lastOrNull() -> Unit // the tail: where the tip already rests
            cell in path -> refuse(cell) // the drawn line is a wall
            hooks.canExtend(path, cell) -> extend(cell)
            else -> {
                val bridge = bridgeTo(cell)
                when {
                    bridge != null -> {
                        extend(bridge)
                        extend(cell)
                    }
                    !fill() -> refuse(cell)
                }
            }
        }
    }

    private fun refuse(cell: GridCell) {
        if (!refused) {
            refused = true
            emit(DragEvent.Refused(cell))
        }
    }

    private fun extend(cell: GridCell) {
        path = path + cell
        redrawn()
        emit(DragEvent.Extend(cell))
    }

    /** The trace changed: how the finger came to it says nothing about the next cell. */
    private fun redrawn() {
        trail.clear()
        route.clear()
        path.lastOrNull()?.let { if (it == visited) route += it }
    }

    /**
     * Draws the trace on along [route] to the caught cell at its end, one the tail cannot reach in a
     * step: every cell the finger crossed on the way, in order, each a step or a corner cut the hooks
     * accept. The first one they refuse stops the trace there, and the fill reports false.
     */
    private fun fill(): Boolean {
        if (route.size < 3) return false // no cell in between: the step and the corner cut were just refused
        for (step in route.subList(1, route.size).toList()) { // a copy: every extend starts the route over
            if (!hooks.canExtend(path, step)) extend(bridgeTo(step) ?: return false)
            extend(step)
        }
        return true
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
 * centre: a plus shape. Along either axis the cell is caught out to [ExtendReach] (any cell
 * the stroke refuses or extends) or [RetractReach] (the cell before the tail, the one step
 * back), but only while the other offset is within [Arm]; the four corners are dead, so a
 * finger skirting a cell's corner on its way to the next one never takes it.
 */
internal object DragTraceCatch {
    /** A move is walked in steps no longer than this (cell pitches), so a fast swipe skips no cell. */
    const val SubStepCells: Float = 0.1f

    /** Past this offset on both axes the finger is in a cell's corner, which catches nothing. */
    const val Arm: Float = 0.25f

    /** How far from its centre a new cell is caught; the rest of the half-pitch is a dead rim that keeps a just-retracted cell from re-extending at once. */
    const val ExtendReach: Float = 0.42f

    /** The cell before the tail is caught right to its edge: stepping back is never the hard part. */
    const val RetractReach: Float = 0.5f

    fun catches(u: Float, v: Float, reach: Float): Boolean {
        val x = abs(u)
        val y = abs(v)
        return maxOf(x, y) <= reach && minOf(x, y) <= Arm
    }
}
