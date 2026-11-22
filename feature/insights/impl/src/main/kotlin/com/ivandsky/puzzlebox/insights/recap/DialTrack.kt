package com.ivandsky.puzzlebox.insights.recap

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The line the ticks stand on: a circle of [radius] pulled apart into a stadium by a [straight] run down each side, so
 * a tall dial is a stretched ring, not an oval whose ticks bunch at the ends. With no run it is the circle.
 */
internal class DialTrack(private val radius: Float, private val straight: Float) {

    /** The track's length, the span of one trip round it. */
    val perimeter: Float = 2f * straight + 2f * PI.toFloat() * radius

    /**
     * The point [fraction] of a trip round the track, clockwise from the top centre; past 1 it goes round again and
     * below 0 it goes back. The arithmetic is in doubles so a ring with no straight run lands where a plain circle's
     * `cos`/`sin` would, tick for tick.
     */
    fun at(fraction: Double): DialPlacement {
        val turn = (fraction % 1.0 + 1.0) % 1.0
        val r = radius.toDouble()
        val run = straight.toDouble()
        val s = turn * (2.0 * run + 2.0 * PI * r)
        val half = run / 2.0
        val quarter = PI / 2.0 * r
        return when {
            // Upper end, the right half of its arc.
            s < quarter -> arc(centerY = -half, angle = -PI / 2.0 + s / r)
            // Down the right side.
            s < quarter + run -> DialPlacement(radius, (-half + (s - quarter)).toFloat(), 1f, 0f)
            // Lower end.
            s < 3.0 * quarter + run -> arc(centerY = half, angle = (s - quarter - run) / r)
            // Up the left side.
            s < 3.0 * quarter + 2.0 * run -> DialPlacement(-radius, (half - (s - 3.0 * quarter - run)).toFloat(), -1f, 0f)
            // Upper end, the left half of its arc.
            else -> arc(centerY = -half, angle = PI + (s - 3.0 * quarter - 2.0 * run) / r)
        }
    }

    private fun arc(centerY: Double, angle: Double): DialPlacement {
        val a = angle.toFloat()
        val nx = cos(a)
        val ny = sin(a)
        return DialPlacement(nx * radius, centerY.toFloat() + ny * radius, nx, ny)
    }
}
