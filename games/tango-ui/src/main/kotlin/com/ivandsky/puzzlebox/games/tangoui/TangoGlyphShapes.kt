package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/** The sun in [fill], centred in the scope, sized from the board size [n]: the one drawing the board and the Spotlight share. */
internal fun DrawScope.drawSun(fill: Color, n: Int) {
    val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction(n)
    val center = Offset(size.width / 2f, size.height / 2f)
    val discRadius = glyph * TangoBoardSpec.sunDiscFraction / 2f
    val rayInner = discRadius + glyph * TangoBoardSpec.sunRayGapFraction
    val rayLength = glyph * TangoBoardSpec.sunRayLengthFraction
    val rayWidth = glyph * TangoBoardSpec.sunRayWidthFraction
    drawCircle(fill, radius = discRadius, center = center)
    for (i in 0 until 8) {
        val angle = Math.toRadians((i * 45.0))
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

/** The moon in [fill], centred in the scope, sized from the board size [n]: the one drawing the board and the Spotlight share. */
internal fun DrawScope.drawMoon(fill: Color, n: Int) {
    val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction(n)
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = glyph * TangoBoardSpec.sunDiscFraction / 2f
    val punch = glyph * TangoBoardSpec.moonPunchDiameterFraction / 2f
    val punchOffset = glyph * TangoBoardSpec.moonPunchOffsetFraction
    val punchCenter = center + Offset(punchOffset, -punchOffset) // toward upper-right; one orientation, never mirrored (§2)
    val disc = Path().apply { addOval(Rect(center = center, radius = radius)) }
    val punchShape = Path().apply { addOval(Rect(center = punchCenter, radius = punch)) }
    // Blend-free crescent: disc minus punch. DstOut inside a scaled
    // offscreen layer renders the punch OPAQUE (Robolectric RNG — the
    // violation-scaled moons showed a white disc), so the shape is
    // subtracted geometrically instead of via a blend mode.
    val crescent = Path().apply { op(disc, punchShape, PathOperation.Difference) }
    drawPath(crescent, fill)
}
