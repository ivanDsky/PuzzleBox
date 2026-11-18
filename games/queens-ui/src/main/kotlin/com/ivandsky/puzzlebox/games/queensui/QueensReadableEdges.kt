package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.BoardGeometry
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.gameui.GridGeometry

/**
 * The edge layer of a board whose [geometry] outlines it at the wall weight, drawn above the opaque region
 * fills: a low-alpha ink line at the shared grid-line weight on every interior cell edge, then the region wall at
 * [QueensBoardDefaults.readableRegionWallWeight] on every edge between two regions, and the wall token at
 * [BoardGeometry.wallWeight] around the board, with round ends. A region reads as
 * one walled shape and the cells inside it stay countable.
 * The [drawRegionTexture] dots go down first, so the lines stay on top.
 *
 * The cell lines are [BoardColors.boardInk] at [QueensBoardDefaults.cellGridLineAlpha], not the opaque grid-line
 * token: that grey sits within 1.6:1 of several region fills, and a tint of ink keeps its contrast on every fill.
 */
internal fun DrawScope.drawWalledEdges(model: QueensBoardModel, colors: BoardColors, geometry: BoardGeometry) {
    drawRegionTexture(model, colors.boardInk)
    val n = model.n
    val g = GridGeometry(n, n, size.width, size.height)
    val thin = BoardTokens.boardGridLineWeight.toPx()
    val faint = colors.boardInk.copy(alpha = QueensBoardDefaults.cellGridLineAlpha)
    for (i in 1 until n) {
        drawLine(faint, Offset(i * g.cellWidth, 0f), Offset(i * g.cellWidth, size.height), thin)
        drawLine(faint, Offset(0f, i * g.cellHeight), Offset(size.width, i * g.cellHeight), thin)
    }
    val weight = QueensBoardDefaults.readableRegionWallWeight.toPx()
    val outline = geometry.wallWeight.toPx()
    val cap = StrokeCap.Round
    // Each interior edge once (right and down neighbours only); round ends of neighbouring edges join into one wall.
    for (row in 0 until n) for (col in 0 until n) {
        val idx = row * n + col
        val region = model.regions[idx]
        if (col + 1 < n && model.regions[idx + 1] != region) {
            val x = (col + 1) * g.cellWidth
            drawLine(colors.boardWall, Offset(x, row * g.cellHeight), Offset(x, (row + 1) * g.cellHeight), weight, cap)
        }
        if (row + 1 < n && model.regions[idx + n] != region) {
            val y = (row + 1) * g.cellHeight
            drawLine(colors.boardWall, Offset(col * g.cellWidth, y), Offset((col + 1) * g.cellWidth, y), weight, cap)
        }
    }
    // Board outline, inset half a stroke so nothing clips outside the canvas.
    drawRect(
        colors.boardWall,
        topLeft = Offset(outline / 2f, outline / 2f),
        size = Size(size.width - outline, size.height - outline),
        style = Stroke(outline, join = StrokeJoin.Round),
    )
}
