package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One drag stroke over the trace [path], as a pure state machine: finger samples in, [DragEvent]s
 * out. [DragTraceBoard] feeds it pointer samples; tests feed it synthetic fingers.
 *
 * The line follows where the finger is relative to the line's end (the tail), not which part of
 * which cell it is over ([DragTraceSteer]). Every move is walked in [DragTraceSteer.SubStepCells]
 * sub-steps and each sub-step repeats the checks until none fires, so a fast swipe steps cell by
 * cell and skips none:
 *
 * - The finger nearly at the middle of the cell before the tail: the trace steps back one cell
 *   ([DragEvent.Retract]). Where the line turns at that cell, the finger nearly at the middle of the
 *   cell before the turn steps it back too: a finger backing round a corner cuts across the corner
 *   cell and never comes near its middle. Any other traced cell is a wall, never cut back to,
 *   crossed or picked up.
 * - The finger [DragTraceSteer.StepOut] past the tail: the trace steps into the neighbour in the
 *   finger's direction on the axis it is further along. When that step would strand a free cell
 *   ([DragTraceHooks.strands]) and the neighbour on the other axis would not, the finger must be
 *   [DragTraceSteer.ClearlyIn] first; then a finger near the diagonal ([DragTraceSteer.DiagonalLean])
 *   turns through the corner, a clearly sideways one takes the stranding step it means.
 * - The hooks refuse that step: the trace turns into the neighbour on the other axis the finger
 *   leans [DragTraceSteer.TurnLean] toward, or, once the finger is clearly in the refused cell, as
 *   far into the side cell. Heading back toward the cell before the tail never turns.
 * - Both refused: the trace stays, and [DragEvent.Refused] shows only once the finger is a full
 *   cell past the tail toward a blocked cell, at most once per [moveTo].
 *
 * The finger steers only once it has been in the tail's cell, a cell diagonal to it, or has taken a
 * cell the hooks accept (a touch-down on the tail, or a drag into it); a drawn cell elsewhere is
 * refused and never picks the line up, and any other cell the hooks refuse is reported
 * [DragEvent.Refused] when entered.
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
    private var steering = false

    /** The cell the finger last came into while it was not steering: a cell is acted on when entered, never while the finger rests in it. */
    private var visited: GridCell? = null

    private var events = mutableListOf<DragEvent>()
    private var refused = false

    /** The stroke starts where the finger landed: on the tail it steers at once, on a cell the hooks accept it takes it. */
    fun begin(): List<DragEvent> = collect {
        visited = cellOf(at)
        enter(cellOf(at))
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
        val steps = ceil((to - from).getDistance() / DragTraceSteer.SubStepCells).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val point = from + (to - from) * (i.toFloat() / steps)
            val cell = cellOf(point)
            if (!steering && cell != visited) {
                visited = cell
                enter(cell)
            }
            if (steering) steer(point)
        }
        at = to
    }

    /**
     * A finger not yet steering comes into [cell]: the tail takes it over, a drawn cell is a wall, a
     * cell the hooks accept is taken, and a cell diagonal to the tail is an ambiguous fresh touch that
     * steers, so the first step out of the tail is chosen as for a finger cutting that corner.
     */
    private fun enter(cell: GridCell) {
        val tail = path.lastOrNull()
        when {
            cell == tail -> steering = true
            cell in path -> refuse(cell)
            takes(cell) -> steering = true
            tail != null && diagonal(cell, tail) -> steering = true
            else -> refuse(cell)
        }
    }

    /** Follows [finger] from the tail until neither a step back nor a step forward is due. */
    private fun steer(finger: Offset) {
        while (path.isNotEmpty() && (stepBack(finger) || stepForward(finger))) Unit
    }

    private fun stepBack(finger: Offset): Boolean {
        val tail = path.last()
        val previous = path.getOrNull(path.lastIndex - 1) ?: return false
        val beforeTurn = path.getOrNull(path.lastIndex - 2)?.takeIf { diagonal(it, tail) }
        val back = nearlyAtMiddle(finger, of = previous, from = tail) ||
            (beforeTurn != null && nearlyAtMiddle(finger, of = beforeTurn, from = previous))
        if (!back) return false
        path = path.dropLast(1)
        emit(DragEvent.Retract(path.lastIndex))
        return true
    }

    /** The finger is [DragTraceSteer.StepBack] from [from]'s centre toward its neighbour [of], near the line between them. */
    private fun nearlyAtMiddle(finger: Offset, of: GridCell, from: GridCell): Boolean {
        val offset = offsetFromTail(finger, from)
        val towardRow = of.row - from.row
        val towardCol = of.col - from.col
        val along = offset.x * towardCol + offset.y * towardRow
        val sideways = abs(offset.x * towardRow) + abs(offset.y * towardCol)
        return along >= DragTraceSteer.StepBack && sideways <= DragTraceSteer.StepBackSideways
    }

    private fun diagonal(a: GridCell, b: GridCell) = abs(a.row - b.row) == 1 && abs(a.col - b.col) == 1

    private fun stepForward(finger: Offset): Boolean {
        val tail = path.last()
        val offset = offsetFromTail(finger, tail)
        val horizontalIsMain = abs(offset.x) >= abs(offset.y)
        val reach = if (horizontalIsMain) abs(offset.x) else abs(offset.y)
        if (reach < DragTraceSteer.StepOut) return false

        val main = neighbour(tail, horizontal = horizontalIsMain, offset = offset)
        val side = neighbour(tail, horizontal = !horizontalIsMain, offset = offset)
        val lean = if (horizontalIsMain) abs(offset.y) else abs(offset.x)
        if (opens(main)) {
            val contested = opens(side) && hooks.strands(path, main) && !hooks.strands(path, side)
            if (!contested) return takes(main)
            if (reach < DragTraceSteer.ClearlyIn) return false
            return takes(if (lean >= reach * DragTraceSteer.DiagonalLean) side else main)
        }
        // Heading back along the line is a step back in the making, never a turn off it.
        if (main == path.getOrNull(path.lastIndex - 1)) return false
        val turnLean = if (reach < DragTraceSteer.ClearlyIn) DragTraceSteer.TurnLean else DragTraceSteer.ClearlyIn
        if (lean >= turnLean && takes(side)) return true
        if (reach >= DragTraceSteer.RefuseReach) refuse(main)
        return false
    }

    private fun offsetFromTail(finger: Offset, tail: GridCell) = finger - Offset(tail.col + 0.5f, tail.row + 0.5f)

    private fun neighbour(tail: GridCell, horizontal: Boolean, offset: Offset): GridCell = when {
        horizontal -> GridCell(tail.row, tail.col + if (offset.x > 0f) 1 else -1)
        else -> GridCell(tail.row + if (offset.y > 0f) 1 else -1, tail.col)
    }

    private fun opens(cell: GridCell) = geometry.isValid(cell) && cell !in path && hooks.canExtend(path, cell)

    private fun takes(cell: GridCell): Boolean {
        if (!opens(cell)) return false
        extend(cell)
        return true
    }

    private fun refuse(cell: GridCell) {
        if (!refused) {
            refused = true
            emit(DragEvent.Refused(cell))
        }
    }

    private fun extend(cell: GridCell) {
        path = path + cell
        emit(DragEvent.Extend(cell))
    }

    private fun emit(event: DragEvent) {
        events += event
    }

    private companion object {
        const val EDGE_INSET = 1e-3f
    }
}

/**
 * How the finger steers the line, in cell pitches measured from the centre of the line's end.
 * Stepping out is easier than turning back on purpose: a wobble at a turn never undoes a cell.
 */
internal object DragTraceSteer {
    /** A move is walked in steps no longer than this, so a fast swipe skips no cell. */
    const val SubStepCells: Float = 0.1f

    /** The finger this far past the tail's centre on its larger axis steps the line forward. */
    const val StepOut: Float = 0.55f

    /** Past this lean on the other axis, a refused forward step turns onto that axis instead... */
    const val TurnLean: Float = 0.15f

    /**
     * ...until the finger is this far into the refused cell, 0.2 past the shared edge: clearly in a
     * cell the line can not enter, the line holds until the finger is as far into the side cell.
     * Also how far a finger must be in before a step that strands a cell is told from its rival.
     */
    const val ClearlyIn: Float = MotionTokens.Trace.ClearlyInCells

    /** A finger leaning at least this share of its reach (within 15° of the diagonal) means either step. */
    const val DiagonalLean: Float = 0.58f

    /**
     * The finger this far toward the cell before the tail, nearly at its middle, steps back; where the
     * line turns at that cell, as far from it toward the cell before the turn steps back too...
     */
    const val StepBack: Float = 0.9f

    /** ...unless it is further than this off the line to that cell. */
    const val StepBackSideways: Float = 0.5f

    /** A refusal shows only once the finger is a whole cell past the tail toward a blocked cell. */
    const val RefuseReach: Float = 1.0f
}
