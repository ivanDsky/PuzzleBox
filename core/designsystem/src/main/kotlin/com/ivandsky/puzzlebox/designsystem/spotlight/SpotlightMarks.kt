package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset

// Draw functions for the Spotlight's marks. Geometry comes from SpotlightLayout, timing from SpotlightSequence.

/** One path over every unlisted cell, so neighbouring dim cells share edges without anti-aliased seams. */
internal fun dimPath(spotlight: Spotlight, lit: Set<Int>, size: Size): Path = Path().apply {
    for (cell in 0 until spotlight.rows * spotlight.cols) {
        if (cell !in lit) addRect(cellRect(cell, spotlight.rows, spotlight.cols, size))
    }
}

/** The hairline as one path of unit [edges]; square caps close the corners where two edges meet. */
internal fun hairlinePath(edges: List<GridEdge>, rows: Int, cols: Int, size: Size): Path = Path().apply {
    val w = size.width / cols
    val h = size.height / rows
    for (edge in edges) {
        moveTo(edge.col0 * w, edge.row0 * h)
        lineTo(edge.col1 * w, edge.row1 * h)
    }
}

internal fun DrawScope.drawHairline(path: Path, color: Color, width: Float) {
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Square))
}

/**
 * The game's glyph for [cell] in [role], scaled by [scale] about the centre of [rect] and drawn in [color]. The glyph
 * draws in a DrawScope whose origin and size are the scaled cell's (its `size` and `center` are the glyph's own), so a
 * glyph that ignores its `size` argument still lands in its cell. Nothing is asked of the glyph while it is invisible.
 */
internal fun DrawScope.drawSpotlightGlyph(
    glyphs: SpotlightGlyphs,
    cell: Int,
    role: SpotlightRole,
    rect: Rect,
    scale: Float,
    color: Color,
) {
    if (color.alpha <= 0f) return
    val dx = rect.width * (scale - 1) / 2
    val dy = rect.height * (scale - 1) / 2
    inset(rect.left - dx, rect.top - dy, size.width - rect.right - dx, size.height - rect.bottom - dy) {
        with(glyphs) { draw(cell, role, size, color) }
    }
}

/**
 * The culprit's minus badge in the top-left corner of [rect] (prototype 07: an error disc ringed by the board
 * surface, an on-error bar), scaled by [scale] about its own centre and faded by [alpha].
 */
internal fun DrawScope.drawCulpritBadge(
    rect: Rect,
    scale: Float,
    alpha: Float,
    disc: Color,
    bar: Color,
    halo: Color,
) {
    if (alpha <= 0f) return
    val side = rect.minDimension * scale
    val center = Offset(rect.left + rect.minDimension * BADGE_INSET, rect.top + rect.minDimension * BADGE_INSET)
    drawCircle(halo, radius = side * BADGE_HALO_RADIUS, center = center, alpha = alpha)
    drawCircle(disc, radius = side * BADGE_DISC_RADIUS, center = center, alpha = alpha)
    val half = side * BADGE_BAR_HALF_LENGTH
    drawLine(
        color = bar,
        start = Offset(center.x - half, center.y),
        end = Offset(center.x + half, center.y),
        strokeWidth = side * BADGE_BAR_WIDTH,
        cap = StrokeCap.Round,
        alpha = alpha,
    )
}

// Badge geometry as fractions of the cell side (prototype 07, 100-unit cell: centre 20, r 16 ± 1.5 halo, bar 15 × 5).
private const val BADGE_INSET = 0.20f
private const val BADGE_HALO_RADIUS = 0.175f
private const val BADGE_DISC_RADIUS = 0.145f
private const val BADGE_BAR_HALF_LENGTH = 0.075f
private const val BADGE_BAR_WIDTH = 0.05f
