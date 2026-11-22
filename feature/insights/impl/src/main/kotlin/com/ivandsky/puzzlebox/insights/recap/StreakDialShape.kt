package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The dial's size in a slot of [maxWidth] by [maxHeight]. A slot as wide as it is tall (the tablet card) gets the
 * circle it always did. A tall narrow one (a phone) keeps the same width but stretches the dial down by up to
 * [DialMaxStretch] times that width, so the ring and its count use the card's height instead of floating in empty
 * bands above and below.
 */
internal fun dialSize(maxWidth: Dp, maxHeight: Dp): DpSize {
    val width = min(maxWidth.value, DialMaxSize.value) - DialInset.value * 2
    val room = maxHeight.value - DialInset.value * 2
    if (maxHeight <= maxWidth) return DpSize(min(width, room).dp, min(width, room).dp)
    return DpSize(width.dp, min(room, width * DialMaxStretch).dp)
}

/** Where a tick sits on the [DialTrack]: [x], [y] from the track's centre, and the outward unit normal ([nx], [ny]). */
internal class DialPlacement(val x: Float, val y: Float, val nx: Float, val ny: Float)

/**
 * The line the ticks stand on: a circle of [radius] pulled apart into a stadium by a [straight] run down each side, so
 * a tall dial is a stretched ring, not an oval whose ticks bunch at the ends. With no run it is the circle.
 */
internal class DialTrack(private val radius: Float, private val straight: Float) {

    /** The track's length, the span of one trip round it. */
    val perimeter: Float = 2f * straight + 2f * PI.toFloat() * radius

    /** The point [fraction] of a trip round the track, clockwise from the top centre; past 1 it goes round again. */
    fun at(fraction: Float): DialPlacement {
        val s = ((fraction % 1f) + 1f) % 1f * perimeter
        val half = straight / 2f
        val quarter = PI.toFloat() / 2f * radius
        return when {
            // Upper end, the right half of its arc.
            s < quarter -> arc(centerY = -half, angle = -PI.toFloat() / 2f + s / radius)
            // Down the right side.
            s < quarter + straight -> DialPlacement(radius, -half + (s - quarter), 1f, 0f)
            // Lower end.
            s < 3f * quarter + straight -> arc(centerY = half, angle = (s - quarter - straight) / radius)
            // Up the left side.
            s < 3f * quarter + 2f * straight -> DialPlacement(-radius, half - (s - 3f * quarter - straight), -1f, 0f)
            // Upper end, the left half of its arc.
            else -> arc(centerY = -half, angle = PI.toFloat() + (s - 3f * quarter - 2f * straight) / radius)
        }
    }

    private fun arc(centerY: Float, angle: Float): DialPlacement {
        val nx = cos(angle)
        val ny = sin(angle)
        return DialPlacement(nx * radius, centerY + ny * radius, nx, ny)
    }
}

private val DialMaxSize = 420.dp
private val DialInset = 8.dp
private const val DialMaxStretch = 1.8f
