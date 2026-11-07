package com.ivandsky.puzzlebox.games.strandsui.board

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The eight ways a trace can step, in clockwise screen order from +x (y grows down), so
 * [ordinal] × 45° is each one's angle. A side step is the cell next to the tail; a diagonal
 * its corner neighbour.
 */
internal enum class TraceDirection(val dRow: Int, val dCol: Int) {
    East(0, 1), SouthEast(1, 1), South(1, 0), SouthWest(1, -1),
    West(0, -1), NorthWest(-1, -1), North(-1, 0), NorthEast(-1, 1),
    ;

    val isDiagonal: Boolean get() = dRow != 0 && dCol != 0

    val degrees: Float get() = ordinal * 45f

    /** Centre to centre, in cell pitches: 1 for a side, √2 for a diagonal. */
    val length: Float get() = if (isDiagonal) DiagonalLength else 1f

    /** Half the cone a travel must fall in to take this direction (A5). */
    val coneDegrees: Float
        get() = if (isDiagonal) StrandsBoardSpec.DiagonalConeDegrees else StrandsBoardSpec.SideConeDegrees

    companion object {
        /**
         * The direction of the travel ([dx], [dy]): a side within [StrandsBoardSpec.SideConeDegrees]
         * of an axis, else a diagonal within [StrandsBoardSpec.DiagonalConeDegrees] of 45°; null
         * between the cones, where a finger has not yet made up its mind.
         */
        fun of(dx: Float, dy: Float): TraceDirection? {
            val deg = degreesOf(dx, dy)
            val m = deg % 90f
            if (minOf(m, 90f - m) <= StrandsBoardSpec.SideConeDegrees) return entries[((deg / 90f).roundToInt() % 4) * 2]
            if (abs(45f - m) <= StrandsBoardSpec.DiagonalConeDegrees) return entries[(floor(deg / 90f).toInt() * 2 + 1) % 8]
            return null
        }

        /** The screen angle of ([dx], [dy]) in [0, 360). */
        fun degreesOf(dx: Float, dy: Float): Float = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f

        /** The smaller angle between two headings, in degrees. */
        fun degreesBetween(a: Float, b: Float): Float {
            val d = abs(a - b) % 360f
            return if (d > 180f) 360f - d else d
        }
    }
}

private val DiagonalLength = sqrt(2f)
