package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.gameui.GridGeometry

/**
 * §3.1 edge layer (F-1), drawn above the opaque region fills: a [QueensBoardDefaults.cellGridLineWeight]
 * hairline on every interior cell edge in low-alpha ink, a full-contrast
 * [QueensBoardDefaults.regionBorderWeight] stroke on every interior edge
 * between two different regions so region shapes read, and the
 * board outline in full ink at [QueensBoardDefaults.regionBorderWeight] (the Tango/Zip frame weight).
 * The [drawRegionTexture] dots go down first, so the lattice and borders stay on top.
 */
internal fun DrawScope.drawCellEdges(model: QueensBoardModel, ink: Color) {
    drawRegionTexture(model, ink)
    val n = model.n
    val g = GridGeometry(n, n, size.width, size.height)
    val hairline = QueensBoardDefaults.cellGridLineWeight.toPx()
    val regionWeight = QueensBoardDefaults.regionBorderWeight.toPx()
    val faint = ink.copy(alpha = QueensBoardDefaults.cellGridLineAlpha)

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Color, width: Float) {
        drawLine(color, Offset(x0, y0), Offset(x1, y1), strokeWidth = width)
    }
    // Hairline lattice: every interior gridline, exactly once.
    for (i in 1 until n) {
        line(i * g.cellWidth, 0f, i * g.cellWidth, size.height, faint, hairline)
        line(0f, i * g.cellHeight, size.width, i * g.cellHeight, faint, hairline)
    }
    // Board outline, inset half a stroke so nothing clips outside the canvas.
    drawRect(
        ink,
        topLeft = Offset(regionWeight / 2f, regionWeight / 2f),
        size = Size(size.width - regionWeight, size.height - regionWeight),
        style = Stroke(width = regionWeight),
    )
    // Interior edges toward a different region: region boundary at full-contrast ink
    // (each interior edge visited once — right/down neighbors only).
    for (row in 0 until n) for (col in 0 until n) {
        val idx = row * n + col
        val region = model.regions[idx]
        if (col + 1 < n && model.regions[idx + 1] != region) {
            val x = (col + 1) * g.cellWidth
            line(x, row * g.cellHeight, x, (row + 1) * g.cellHeight, ink, regionWeight)
        }
        if (row + 1 < n && model.regions[idx + n] != region) {
            val y = (row + 1) * g.cellHeight
            line(col * g.cellWidth, y, (col + 1) * g.cellWidth, y, ink, regionWeight)
        }
    }
}

/**
 * Ticket 20 dot texture: four static dots per cell of every [QueensRegionPalette.textured]
 * region (ids 10+ repeat the 10-hue cycle), so K/L never read as A/B on 11×11 and 12×12.
 */
internal fun DrawScope.drawRegionTexture(model: QueensBoardModel, ink: Color) {
    val n = model.n
    val g = GridGeometry(n, n, size.width, size.height)
    val cell = minOf(g.cellWidth, g.cellHeight)
    val radius = maxOf(QueensRegionPalette.textureDotMinRadius.toPx(), cell * QueensRegionPalette.textureDotRadiusFraction)
    val dot = ink.copy(alpha = QueensRegionPalette.textureDotAlpha)
    for (idx in 0 until n * n) {
        if (!QueensRegionPalette.textured(model.regions[idx])) continue
        drawTextureDots(dot, radius, (idx % n) * g.cellWidth, (idx / n) * g.cellHeight, g.cellWidth, g.cellHeight)
    }
}

/** The texture's four [color] dots of [radius] in the [width]×[height] box at [left], [top]. */
internal fun DrawScope.drawTextureDots(color: Color, radius: Float, left: Float, top: Float, width: Float, height: Float) {
    val near = QueensRegionPalette.textureDotOffset
    val far = 1f - near
    drawCircle(color, radius, Offset(left + width * near, top + height * near))
    drawCircle(color, radius, Offset(left + width * near, top + height * far))
    drawCircle(color, radius, Offset(left + width * far, top + height * near))
    drawCircle(color, radius, Offset(left + width * far, top + height * far))
}

internal fun DrawScope.drawX(color: Color, weightPx: Float, lengthFraction: Float, scale: Float = 1f, alpha: Float = 1f) {
    val length = size.minDimension * lengthFraction * scale
    val half = length / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawLine(color, Offset(cx - half, cy - half), Offset(cx + half, cy + half), weightPx * scale, StrokeCap.Round, alpha = alpha)
    drawLine(color, Offset(cx - half, cy + half), Offset(cx + half, cy - half), weightPx * scale, StrokeCap.Round, alpha = alpha)
}

/** Crown silhouette, single path: three points + band base, height = fraction of cell.
 *  [style] Fill is a placed queen; a dashed Stroke is the ghost, "a queen goes here".
 *  [path] is the caller's scratch, rewound on every draw and held across them so a redrawing caller allocates it once. */
internal fun DrawScope.drawQueen(color: Color, heightFraction: Float, alpha: Float, path: Path, style: DrawStyle = Fill) {
    buildQueenPath(heightFraction, path)
    drawPath(path, color.copy(alpha = alpha), style = style)
}

/** Rewinds [path] and builds the crown and its band into it for a cell-centred glyph [heightFraction] of the cell
 *  high, so a caller drawing the same crown twice builds it once. No allocation: the band is plain lines. */
internal fun DrawScope.buildQueenPath(heightFraction: Float, path: Path) {
    val h = size.minDimension * heightFraction
    val w = h * 1.15f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val bandTop = top + h * 0.70f
    val bandBottom = bandTop + h * 0.16f
    path.apply {
        rewind()
        moveTo(left, top + h * 0.62f) // left base of crown body
        lineTo(left, top + h * 0.18f)
        lineTo(left + w * 0.26f, top + h * 0.42f) // left valley
        lineTo(left + w * 0.5f, top) // center peak
        lineTo(left + w * 0.74f, top + h * 0.42f) // right valley
        lineTo(left + w, top + h * 0.18f)
        lineTo(left + w, top + h * 0.62f)
        close()
        moveTo(left, bandTop) // band, same winding as addRect
        lineTo(left, bandBottom)
        lineTo(left + w, bandBottom)
        lineTo(left + w, bandTop)
        close()
    }
}

/** §3.5: 45° diagonal bands — a [scrimBand]-wide scrim margin around each [band]-wide error band, one every [pitch].
 *  Black under red guarantees the composite clears 3:1 against any fill lightness. */
internal fun DrawScope.drawViolationStripes(error: Color, scrim: Color, pitch: Float, band: Float, scrimBand: Float) {
    val diagonal = size.width + size.height
    var start = -size.height
    while (start < diagonal) {
        drawLine(scrim, Offset(start, size.height), Offset(start + size.height, 0f), strokeWidth = band + scrimBand)
        drawLine(error, Offset(start + scrimBand / 2f, size.height), Offset(start + size.height + scrimBand / 2f, 0f), strokeWidth = band)
        start += pitch
    }
}
