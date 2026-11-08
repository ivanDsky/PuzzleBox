package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * The live tip (spec A4): [extent] of the step from the tail's centre toward [toward] — the
 * neighbour the tip leans at (the next cell for [TipKind.Forward], the previous one for
 * [TipKind.Back]). [extent] is in [0, [MotionTokens.Trace.TipMaxCells]], so the tip never
 * reaches a not-yet-committed centre.
 */
data class TraceTip<C : Any>(val kind: TipKind, val toward: C, val extent: Float)

/**
 * The ONE direction [offset] (cell units from the tail centre) points at: of the four grid
 * axes, the one with the strictly larger component — a diagonal finger names no axis, so a
 * 4-way tip rests ([eightWay] false) — or of the eight compasses, the nearest ([eightWay]
 * true). Null inside the dead zone around the centre: a resting finger leans nowhere.
 */
fun dominantDirection(offset: Offset, eightWay: Boolean): TipDirection? {
    if (offset.getDistance() < MotionTokens.Trace.TipDeadCells) return null
    if (!eightWay) {
        val alongX = abs(offset.x)
        val alongY = abs(offset.y)
        return when {
            alongX > alongY -> if (offset.x > 0f) TipDirection.East else TipDirection.West
            alongY > alongX -> if (offset.y > 0f) TipDirection.South else TipDirection.North
            else -> null
        }
    }
    val angle = atan2(offset.y, offset.x) * 180f / PI.toFloat()
    return TipDirection.entries[Math.floorMod((angle / 45f).roundToInt(), 8)]
}

/**
 * The tip for a trace over [path] whose finger sits [offset] cell units from the tail's
 * centre, pointing [direction]: it leans along that ONE direction only at a cell the game
 * would take — [canExtend] — or at the previous cell (a retract); toward anything else (a
 * wall, the edge, a used cell, an out-of-order target) there is no tip and the line rests
 * on the tail. The lean is the finger's push along the direction's axis, so nothing
 * off-axis moves it. Null for an empty path.
 */
fun <C : Any> traceTip(
    path: List<C>,
    offset: Offset,
    direction: TipDirection,
    neighbour: (cell: C, direction: TipDirection) -> C,
    canExtend: (next: C) -> Boolean,
): TraceTip<C>? {
    val tail = path.lastOrNull() ?: return null
    val next = neighbour(tail, direction)
    val kind = when {
        next == path.getOrNull(path.lastIndex - 1) -> TipKind.Back
        canExtend(next) -> TipKind.Forward
        else -> return null
    }
    return TraceTip(kind, next, direction.extentOf(offset))
}
