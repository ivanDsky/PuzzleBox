package com.ivandsky.puzzlebox.games.tangoui

import android.graphics.Path as PlatformPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sun in [fill], centred in the scope, sized from the board size [n]: the one drawing the board and the Spotlight
 * share. [rim] grows every edge outward by that many px (0 on the board), so a cover drawn over it hides its fringe.
 */
internal fun DrawScope.drawSun(fill: Color, n: Int, rim: Float = 0f) {
    val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction(n)
    val center = Offset(size.width / 2f, size.height / 2f)
    val discRadius = glyph * TangoBoardSpec.sunDiscFraction / 2f
    val rayInner = discRadius + glyph * TangoBoardSpec.sunRayGapFraction
    val rayLength = glyph * TangoBoardSpec.sunRayLengthFraction
    val rayWidth = glyph * TangoBoardSpec.sunRayWidthFraction + rim * 2
    drawCircle(fill, radius = discRadius + rim, center = center)
    for (i in 0 until TangoBoardSpec.sunRayCount) {
        val angle = Math.toRadians(i * 360.0 / TangoBoardSpec.sunRayCount)
        val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(
            fill,
            center + dir * rayInner,
            center + dir * (rayInner + rayLength),
            strokeWidth = rayWidth,
            cap = StrokeCap.Round,
        )
    }
}

/** The paths [drawMoon] builds its crescent from, rewound on every draw so a redrawing caller allocates them once. */
internal class MoonPaths {
    val disc = Path()
    val punch = Path()
    val crescent = Path()
    private var rimStroke = Stroke(0f)

    /** A [width] px stroke, kept while the width holds so a pulsing culprit allocates none per frame. */
    fun rim(width: Float): Stroke = rimStroke.takeIf { it.width == width } ?: Stroke(width).also { rimStroke = it }
}

/**
 * The moon in [fill], centred in the scope, sized from the board size [n]: the one drawing the board and the Spotlight
 * share. [paths] is the caller's scratch, held across draws; the scope draws on one thread. [rim] grows every edge
 * outward by that many px (0 on the board) with a stroke of the same crescent, inner arc included.
 */
internal fun DrawScope.drawMoon(fill: Color, n: Int, paths: MoonPaths, rim: Float = 0f) {
    val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction(n)
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = glyph * TangoBoardSpec.moonDiscFraction / 2f
    val punch = glyph * TangoBoardSpec.moonPunchDiameterFraction / 2f
    val punchOffset = glyph * TangoBoardSpec.moonPunchOffsetFraction
    val punchCenter = center + Offset(punchOffset, -punchOffset) // toward upper-right; one orientation, never mirrored (§2)
    val disc = paths.disc.apply { rewind(); addCircle(center, radius) }
    val punchShape = paths.punch.apply { rewind(); addCircle(punchCenter, punch) }
    // Blend-free crescent: disc minus punch. DstOut inside a scaled
    // offscreen layer renders the punch OPAQUE (Robolectric RNG — the
    // violation-scaled moons showed a white disc), so the shape is
    // subtracted geometrically instead of via a blend mode.
    val crescent = paths.crescent.apply { rewind(); op(disc, punchShape, PathOperation.Difference) }
    drawPath(crescent, fill)
    if (rim > 0f) drawPath(crescent, fill, style = paths.rim(rim * 2))
}

/** Compose's `addOval(Rect)` without the Rect: the same platform call, so the board's pixels are unchanged. */
private fun Path.addCircle(center: Offset, radius: Float) = asAndroidPath()
    .addOval(center.x - radius, center.y - radius, center.x + radius, center.y + radius, PlatformPath.Direction.CCW)
