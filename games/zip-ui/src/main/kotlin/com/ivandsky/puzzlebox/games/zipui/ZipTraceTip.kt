package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** The four grid directions a Zip tip can take, in clockwise screen order from +x. */
internal enum class TipAxis(val dRow: Int, val dCol: Int) {
    Right(0, 1), Down(1, 0), Left(0, -1), Up(-1, 0),
}

internal enum class TipKind {
    /** Toward a cell the game would accept: the line end follows the finger. */
    Forward,

    /** Toward the previous cell: the line shortens under the finger. */
    Back,

    /** Toward a wall, the edge, a used cell or an out-of-order waypoint (A4). */
    Nudge,
}

/** The live tip: [extent] cells from the tail centre along [axis]. */
internal data class TraceTip(val kind: TipKind, val axis: TipAxis, val extent: Float)

/**
 * Projects [finger] (in cell units: x = column, y = row) onto ONE grid axis from the
 * centre of [path]'s tail. [latch] is the previous frame's axis: the tip keeps it until
 * the finger is [MotionTokens.Trace.TipLatchDegrees4Way] past the sector edge. Null when
 * the path is empty or the finger rests on the tail centre.
 */
internal fun traceTip(
    path: List<GridCell>,
    finger: Offset,
    rows: Int,
    cols: Int,
    hooks: DragTraceHooks,
    latch: TipAxis?,
): TraceTip? {
    val tail = path.lastOrNull() ?: return null
    val vx = finger.x - (tail.col + 0.5f)
    val vy = finger.y - (tail.row + 0.5f)
    if (vx * vx + vy * vy < MotionTokens.Trace.TipDeadCells * MotionTokens.Trace.TipDeadCells) return null

    val angle = atan2(vy, vx)
    val sector = (PI / 2).toFloat()
    val nearest = TipAxis.entries[((angle / sector).roundToInt() % 4 + 4) % 4]
    val hysteresis = MotionTokens.Trace.TipLatchDegrees4Way * (PI / 180).toFloat()
    val axis = if (latch != null && angleBetween(angle, latch.ordinal * sector) <= sector / 2 + hysteresis) latch else nearest

    val next = GridCell(tail.row + axis.dRow, tail.col + axis.dCol)
    val onBoard = next.row in 0 until rows && next.col in 0 until cols
    val kind = when {
        onBoard && next == path.getOrNull(path.lastIndex - 1) -> TipKind.Back
        onBoard && hooks.canExtend(path, next) -> TipKind.Forward
        else -> TipKind.Nudge
    }
    val push = max(0f, vx * axis.dCol + vy * axis.dRow)
    return TraceTip(kind, axis, if (kind == TipKind.Nudge) nudge(push) else follow(push))
}

/** 1:1 up to the follow distance, then a soft approach to the reach cap. */
private fun follow(push: Float): Float {
    val linear = MotionTokens.Trace.TipFollowCells
    val span = MotionTokens.Trace.TipReachCells - linear
    return if (push <= linear) push else linear + span * (1 - exp(-(push - linear) / span))
}

private fun nudge(push: Float): Float =
    MotionTokens.Trace.TipNudgeCells * (1 - exp(-push / MotionTokens.Trace.TipNudgeRiseCells))

private fun angleBetween(a: Float, b: Float): Float {
    val d = abs(a - b) % (2 * PI).toFloat()
    return if (d > PI) (2 * PI).toFloat() - d else d
}
