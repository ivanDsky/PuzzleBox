package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val GLYPH_GRID = 24f
private const val GLYPH_STROKE = 2f

/**
 * Builds a game glyph on the 24 grid in the icon grammar (identity §2.5): [stroke] is
 * SVG path data drawn with a 2dp round stroke; [fill] is path data both filled and
 * stroked (the dots ≤5dp). Colour comes from the Icon tint.
 */
fun gameGlyph(name: String, stroke: String, fill: String = ""): ImageVector {
    val builder = ImageVector.Builder(
        name = "GameGlyph.$name",
        defaultWidth = GLYPH_GRID.dp,
        defaultHeight = GLYPH_GRID.dp,
        viewportWidth = GLYPH_GRID,
        viewportHeight = GLYPH_GRID,
    )
    builder.addPath(
        pathData = addPathNodes(stroke),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = GLYPH_STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    if (fill.isNotEmpty()) {
        builder.addPath(
            pathData = addPathNodes(fill),
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = GLYPH_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}
