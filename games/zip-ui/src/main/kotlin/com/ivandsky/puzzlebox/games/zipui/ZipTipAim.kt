package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.TipDirection
import com.ivandsky.puzzlebox.gameui.TipKind
import com.ivandsky.puzzlebox.gameui.TraceTip
import kotlin.math.abs

/** The steps a Zip line can take from its tail; the first listed wins a finger equally near two. */
private val Arms = arrayOf(TipDirection.East, TipDirection.South, TipDirection.West, TipDirection.North)

/** How far behind the finger its heading is taken from (cell units): past sample jitter, short of a turn. */
private const val TurnLeashCells = 0.1f

/** A heading toward a step at least this share of its speed across it (30° off the travelled axis) turns onto it. */
private const val TurnSlope = 0.58f

/**
 * Zip's live tip (spec A4) for one board: the line end sits at the point nearest the finger
 * on a step out of the tail that Zip would take — a cell [DragTraceHooks.canExtend] accepts,
 * or the previous cell — so it follows the finger from the tail centre out, whichever side of
 * the centre line the finger rides. A wall, the board edge, a used cell or an out-of-order
 * waypoint is no step: the end never leans at one, and a finger clearly inside one
 * ([MotionTokens.Trace.ClearlyInCells]) rests the end on the tail, where the stroke holds the
 * line, rather than leaning it into a side step the finger is not in.
 *
 * The end keeps to the axis the finger travels along. A finger passing a centre off the
 * centre line is, for a moment, nearer a side step than the one it follows; taking that step
 * would stop the end on the centre and poke it sideways. So the end changes axis only when
 * the finger heads toward the nearer step ([TurnSlope]): a fast finger cuts a corner on the
 * diagonal, and an end that waited for it to run straight along the new axis would trail
 * it by half a cell, then snap round the corner. A lifted finger forgets the axis.
 */
internal class ZipTipAim {
    // The steps out of the tail, surveyed once per path: a path is a new list on every commit.
    private var armsOf: List<GridCell>? = null
    private var armsBy: DragTraceHooks? = null
    private val kinds = arrayOfNulls<TipKind>(Arms.size)
    private val cells = arrayOfNulls<GridCell>(Arms.size)

    private var alongRow: Boolean? = null
    private var trailing = Offset.Unspecified

    /** The tip for [finger], in cell units from the board's top-left; null with no finger, no path or no step to take. */
    fun tip(path: List<GridCell>, finger: Offset?, hooks: DragTraceHooks): TraceTip<GridCell>? {
        val tail = path.lastOrNull()
        if (tail == null || finger == null) {
            alongRow = null
            trailing = Offset.Unspecified
            return null
        }
        if (path !== armsOf || hooks !== armsBy) survey(path, tail, hooks)
        val heading = headingOf(finger)
        val offset = finger - Offset(tail.col + 0.5f, tail.row + 0.5f)

        var nearest = -1
        var nearestGap = Float.MAX_VALUE
        var kept = -1
        var keptGap = Float.MAX_VALUE
        for (arm in Arms.indices) {
            if (kinds[arm] == null) continue
            val gap = (offset - Arms[arm].reach(offset)).getDistanceSquared()
            if (gap < nearestGap) {
                nearest = arm
                nearestGap = gap
            }
            if (alongRow == Arms[arm].alongRow && gap < keptGap) {
                kept = arm
                keptGap = gap
            }
        }
        if (nearest < 0) return null
        val turning = Arms[nearest].isTurnedTo(heading)
        val pick = if (kept >= 0 && !(nearestGap < keptGap && turning)) kept else nearest
        alongRow = Arms[pick].alongRow
        val extent = if (heldAt(offset)) 0f else Arms[pick].extentOf(offset)
        return TraceTip(kinds[pick] ?: return null, cells[pick] ?: return null, extent)
    }

    /** Is the finger clearly inside the cell past the tail on its larger axis, and is that cell no step? */
    private fun heldAt(offset: Offset): Boolean {
        val alongX = abs(offset.x) >= abs(offset.y)
        if ((if (alongX) abs(offset.x) else abs(offset.y)) < MotionTokens.Trace.ClearlyInCells) return false
        val ahead = when {
            alongX -> if (offset.x >= 0f) TipDirection.East else TipDirection.West
            offset.y >= 0f -> TipDirection.South
            else -> TipDirection.North
        }
        return kinds[Arms.indexOf(ahead)] == null
    }

    private fun survey(path: List<GridCell>, tail: GridCell, hooks: DragTraceHooks) {
        armsOf = path
        armsBy = hooks
        val previous = path.getOrNull(path.lastIndex - 1)
        for (arm in Arms.indices) {
            val cell = GridCell(tail.row + Arms[arm].dRow, tail.col + Arms[arm].dCol)
            cells[arm] = cell
            kinds[arm] = when {
                cell == previous -> TipKind.Back
                hooks.canExtend(path, cell) -> TipKind.Forward
                else -> null
            }
        }
    }

    /** Where the finger is heading: away from a point dragged behind it on a [TurnLeashCells] leash. */
    private fun headingOf(finger: Offset): Offset {
        if (trailing.isUnspecified) trailing = finger
        val pull = finger - trailing
        val length = pull.getDistance()
        if (length > TurnLeashCells) trailing = finger - pull * (TurnLeashCells / length)
        return finger - trailing
    }
}

private val TipDirection.alongRow: Boolean get() = dRow == 0

/** Does [heading] run toward this step, at least [TurnSlope] as fast as across it? */
private fun TipDirection.isTurnedTo(heading: Offset): Boolean {
    val toward = heading.x * dCol + heading.y * dRow
    val across = if (alongRow) abs(heading.y) else abs(heading.x)
    return toward > 0f && toward >= TurnSlope * across
}

/** The point on this step nearest [offset], both from the tail centre in cell units. */
private fun TipDirection.reach(offset: Offset): Offset {
    val extent = extentOf(offset)
    return Offset(dCol * extent, dRow * extent)
}
