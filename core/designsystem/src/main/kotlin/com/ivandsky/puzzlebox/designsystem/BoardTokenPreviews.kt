package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

// Approval sheet for the board tokens (nav-rethink B3): the board on a small grid, in the theme's light or dark
// look. It draws only from LocalBoardColors / LocalBoardGeometry, the same two locals a game board reads, so what
// the user approves is what the theme provides.

private const val SheetGrid = 4

/** One light-or-dark approval sheet: the [BoardTokenSample] under its title. */
@Composable
internal fun BoardTokenSheet(dark: Boolean) {
    PuzzleBoxTheme(darkTheme = dark) {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Board", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            BoardTokenSample()
        }
    }
}

/**
 * A 4×4 board in the ambient board look: grid lines, the wall outline, two strong walls (one with a corner), a numbered cell
 * for the start, the next number to reach (ringed) and the last, an empty cell
 * and a legal-cell tint sample.
 */
@Composable
internal fun BoardTokenSample() {
    val colors = LocalBoardColors.current
    val geometry = LocalBoardGeometry.current
    val measurer = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.titleMedium.copy(color = colors.boardSurface)
    Canvas(Modifier.size(150.dp).background(colors.boardSurface)) {
        val inset = 8.dp.toPx()
        val pitch = (size.width - 2 * inset) / SheetGrid
        fun point(col: Int, row: Int) = Offset(inset + col * pitch, inset + row * pitch)

        // A legal cell and an empty one: the tint is transparent, so neither changes the cell.
        drawRect(colors.legalCellTint, point(1, 1), Size(pitch, pitch))

        // The board edge is a wall, so only the inner lines are grid lines.
        for (i in 1 until SheetGrid) {
            drawLine(colors.boardGridLine, point(i, 0), point(i, SheetGrid), BoardTokens.boardGridLineWeight.toPx())
            drawLine(colors.boardGridLine, point(0, i), point(SheetGrid, i), BoardTokens.boardGridLineWeight.toPx())
        }

        val wall = Stroke(
            width = geometry.wallWeight.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        // Walls end on the outline's centre line, so the round cap sits inside the outline's own stroke.
        drawPath(wallPath(::point, listOf(2 to 0, 2 to 2, 3 to 2)), colors.boardWall, style = wall)
        drawPath(wallPath(::point, listOf(0 to 3, 2 to 3)), colors.boardWall, style = wall)
        drawRect(colors.boardWall, point(0, 0), Size(pitch * SheetGrid, pitch * SheetGrid), style = wall)

        numberedCell(measurer, numberStyle, colors.boardInk, point(0, 0), pitch, "1")
        numberedCell(measurer, numberStyle, colors.boardInk, point(2, 1), pitch, "2")
        numberedCell(measurer, numberStyle, colors.boardInk, point(3, 3), pitch, "3")
        val center = point(2, 1) + Offset(pitch / 2, pitch / 2)
        drawCircle(colors.boardTarget, radius = pitch * 0.42f, center = center, style = Stroke(3.dp.toPx()))
    }
}

private fun wallPath(point: (Int, Int) -> Offset, corners: List<Pair<Int, Int>>): Path = Path().apply {
    corners.forEachIndexed { index, (col, row) ->
        val at = point(col, row)
        if (index == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
    }
}

private fun DrawScope.numberedCell(
    measurer: TextMeasurer,
    style: TextStyle,
    ink: Color,
    topLeft: Offset,
    pitch: Float,
    label: String,
) {
    val center = topLeft + Offset(pitch / 2, pitch / 2)
    drawCircle(ink, radius = pitch * 0.30f, center = center)
    val text = measurer.measure(label, style)
    drawText(text, topLeft = center - Offset(text.size.width / 2f, text.size.height / 2f))
}

@ShowkaseComposable(name = "BoardTokenSheet", group = "Tokens")
@PuzzleBoxPreviews
@Composable
internal fun BoardTokenSheetPreview() {
    PuzzleBoxTheme { BoardTokenSheet(dark = LocalDarkTheme.current) }
}
