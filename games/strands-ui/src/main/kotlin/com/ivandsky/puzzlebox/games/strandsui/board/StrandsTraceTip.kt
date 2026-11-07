package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strands.Coord
import kotlin.math.exp
import kotlin.math.max

/** The dragging finger in cell units (x = column, y = row): where it is and its [StrandsTraceWalk.travel]. */
internal data class TraceFinger(val at: Offset, val travel: Offset?)

internal enum class TipKind {
    /** Toward a free letter: the line end follows the finger. */
    Forward,

    /** Toward the previous letter: the line shortens under the finger. */
    Back,

    /** Off the board or into a used letter (A4): a short nudge, never a stretch. */
    Nudge,
}

/** The live tip: [extent] of the step from the tail's centre toward the next centre along [direction]. */
internal data class TraceTip(val kind: TipKind, val direction: TraceDirection, val extent: Float)

/**
 * Projects [finger] onto ONE of the eight directions from the centre of [path]'s tail, the
 * one its travel resolves to under the same cones the walk joins by. [latch] is the previous
 * frame's direction: the tip keeps it until the travel is [MotionTokens.Trace.TipLatchDegrees8Way]
 * past its cone, and between cones. Null when the path is empty, the finger rests on the tail
 * centre, or no direction has been resolved yet.
 */
internal fun strandsTip(path: List<Coord>, finger: TraceFinger, rows: Int, cols: Int, latch: TraceDirection?): TraceTip? {
    val tail = path.lastOrNull() ?: return null
    val fromTail = finger.at - centreOf(tail)
    if (fromTail.getDistance() < MotionTokens.Trace.TipDeadCells) return null

    val heading = finger.travel ?: fromTail
    val degrees = TraceDirection.degreesOf(heading.x, heading.y)
    val resolved = TraceDirection.of(heading.x, heading.y)
    val keepLatch = latch != null && latch != resolved &&
        TraceDirection.degreesBetween(degrees, latch.degrees) <= latch.coneDegrees + MotionTokens.Trace.TipLatchDegrees8Way
    val direction = (if (keepLatch) latch else resolved) ?: latch ?: return null

    val next = Coord(tail.row + direction.dRow, tail.col + direction.dCol)
    val onBoard = next.row in 0 until rows && next.col in 0 until cols
    val kind = when {
        onBoard && next == path.getOrNull(path.size - 2) -> TipKind.Back
        onBoard && next !in path -> TipKind.Forward
        else -> TipKind.Nudge
    }
    val step = direction.length
    val push = max(0f, (fromTail.x * direction.dCol + fromTail.y * direction.dRow) / step)
    val extent = if (kind == TipKind.Nudge) nudge(push) / step else follow(push / step)
    return TraceTip(kind, direction, extent)
}

/** 1:1 up to the follow distance, then a soft approach to the reach cap; in fractions of the step. */
private fun follow(push: Float): Float {
    val linear = MotionTokens.Trace.TipFollowCells
    val span = MotionTokens.Trace.TipReachCells - linear
    return if (push <= linear) push else linear + span * (1 - exp(-(push - linear) / span))
}

private fun nudge(push: Float): Float =
    MotionTokens.Trace.TipNudgeCells * (1 - exp(-push / MotionTokens.Trace.TipNudgeRiseCells))
