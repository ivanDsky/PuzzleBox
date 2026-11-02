package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Which interior edge of a cell the overlay attaches to: toward the right or down neighbor. */
enum class EdgeSide { RIGHT, DOWN }

/**
 * Decorations drawn on an interior board edge (F1 §3 "walls/regions/borders overlays").
 * All named roster consumers fit: Zip [Wall]s, Queens region [Border]s, Tango '='/'×' [Mark]s.
 * [cell] is the upper/left cell of the pair the edge separates.
 */
sealed interface EdgeOverlay {
    data class Wall(val cell: GridCell, val side: EdgeSide) : EdgeOverlay
    data class Border(val cell: GridCell, val side: EdgeSide, val color: Color? = null) : EdgeOverlay
    data class Mark(val cell: GridCell, val side: EdgeSide, val symbol: String) : EdgeOverlay
}

/**
 * The shared puzzle board: an explicit rows x cols grid (finding F3-②) with edge overlays
 * and content-agnostic cell slots. The board owns geometry and overlays only — every game
 * decision stays in its engine module (A16). Tap games (Queens/Tango/MiniSudoku) wire
 * [onCellTap]; trace games use [DragTraceBoard] instead.
 */
@Composable
fun BoardGrid(
    rows: Int,
    cols: Int,
    modifier: Modifier = Modifier,
    edgeOverlays: List<EdgeOverlay> = emptyList(),
    onCellTap: ((row: Int, col: Int) -> Unit)? = null,
    cellContent: @Composable BoxScope.(row: Int, col: Int) -> Unit = { _, _ -> },
) {
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val wallColor = MaterialTheme.colorScheme.onSurface
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markStyle = MaterialTheme.typography.labelMedium
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier.aspectRatio(ratio = cols.toFloat() / rows.toFloat()),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGrid(rows, cols, gridLineColor)
            edgeOverlays.forEach { overlay ->
                when (overlay) {
                    is EdgeOverlay.Wall -> drawWall(overlay, rows, cols, wallColor, 4.dp.toPx())
                    is EdgeOverlay.Border -> drawEdgeLine(overlay, rows, cols, overlay.color ?: gridLineColor, 1.dp.toPx())
                    is EdgeOverlay.Mark -> drawEdgeMark(overlay, rows, cols, markColor, textMeasurer, markStyle)
                }
            }
        }
        Cells(rows, cols, cellContent)
        if (onCellTap != null) {
            // Tap layer inside the board box: matchParentSize (DragTrace overlay pattern)
            // keeps hit-testing geometry aligned with the drawn grid, and the component
            // stays a single layout node. GridGeometry does the math, tested on the JVM.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(onCellTap, rows, cols) {
                        detectTapGestures { offset ->
                            val cell = GridGeometry(rows, cols, size.width.toFloat(), size.height.toFloat())
                                .cellAt(offset)
                            onCellTap(cell.row, cell.col)
                        }
                    },
            )
        }
    }
}

@Composable
private fun Cells(
    rows: Int,
    cols: Int,
    content: @Composable BoxScope.(row: Int, col: Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(cols) { col ->
                    Box(Modifier.fillMaxHeight().weight(1f)) {
                        content(row, col)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGrid(rows: Int, cols: Int, color: Color) {
    val geometry = GridGeometry(rows, cols, size.width, size.height)
    for (row in 1 until rows) {
        val y = row * geometry.cellHeight
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
    for (col in 1 until cols) {
        val x = col * geometry.cellWidth
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
    }
}

private fun DrawScope.drawWall(wall: EdgeOverlay.Wall, rows: Int, cols: Int, color: Color, widthPx: Float) {
    val rect = GridGeometry(rows, cols, size.width, size.height).cellRect(wall.cell)
    when (wall.side) {
        EdgeSide.RIGHT -> drawLine(color, Offset(rect.right, rect.top), Offset(rect.right, rect.bottom), widthPx, StrokeCap.Round)
        EdgeSide.DOWN -> drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom), widthPx, StrokeCap.Round)
    }
}

private fun DrawScope.drawEdgeLine(edge: EdgeOverlay.Border, rows: Int, cols: Int, color: Color, widthPx: Float) {
    val rect = GridGeometry(rows, cols, size.width, size.height).cellRect(edge.cell)
    when (edge.side) {
        EdgeSide.RIGHT -> drawLine(color, Offset(rect.right, rect.top), Offset(rect.right, rect.bottom), widthPx)
        EdgeSide.DOWN -> drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom), widthPx)
    }
}

private fun DrawScope.drawEdgeMark(
    mark: EdgeOverlay.Mark,
    rows: Int,
    cols: Int,
    color: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
) {
    val geometry = GridGeometry(rows, cols, size.width, size.height)
    val rect = geometry.cellRect(mark.cell)
    val mid = when (mark.side) {
        EdgeSide.RIGHT -> Offset(rect.right, rect.center.y)
        EdgeSide.DOWN -> Offset(rect.center.x, rect.bottom)
    }
    val measured = textMeasurer.measure(mark.symbol, style.copy(color = color))
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            mid.x - measured.multiParagraph.width / 2f,
            mid.y - measured.multiParagraph.height / 2f,
        ),
    )
}
