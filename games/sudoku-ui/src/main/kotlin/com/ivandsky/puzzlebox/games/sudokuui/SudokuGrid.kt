package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import com.ivandsky.puzzlebox.designsystem.BoardGeometry
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.LocalBoardGeometry
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.games.sudokuui.hint.withDigitStyle

/**
 * The N x N board inside the square [modifier] gives it. The outer frame and box borders share
 * the thick stroke, cell borders the thin one; all lines draw over the cell fills. The colours are the shared board
 * tokens (the thick lines the board ink, the thin ones the gridline role, which holds up on every highlight tint)
 * and the weights and line ends come from the board geometry, so the grid follows the board look without knowing
 * which one is on. Text scales with the cell so any size the caller picks stays legible; digits use tabular figures.
 *
 * [spotlight] draws in a sibling layer over the cells and the lines, inside the same frame padding, so its cells are
 * the grid's cells; its digits are measured in the grid's digit style. It takes no taps, so a tap on a lit or dimmed
 * cell still selects it. Null composes nothing. [overlay] is a layer of the caller's over the same cells, drawn above the
 * [spotlight]; it gets a function that measures a Spotlight's digits in the grid's digit style, and it decides what
 * touches it takes.
 */
@Composable
fun SudokuGrid(
    model: SudokuBoardModel,
    onSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    spotlight: Spotlight? = null,
    overlay: (@Composable BoxScope.(restyle: (Spotlight) -> Spotlight) -> Unit)? = null,
) {
    val geometry = LocalBoardGeometry.current
    val strokes = gridStrokes(geometry)
    val board = LocalBoardColors.current
    val cellLine = board.boardGridLine
    val boxLine = board.boardInk
    Box(
        modifier
            .aspectRatio(1f)
            .semantics { collectionInfo = CollectionInfo(model.size, model.size) }
            .testTag(GRID_TAG),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawGridLines(model, strokes, cellLine, boxLine)
                }
                .padding(strokes.box),
        ) {
            val grid = gridMetrics(min(maxWidth, maxHeight), model.size)
            val cell = grid.cell
            val noteSlot = min(cell / model.boxRows, cell / model.boxCols)
            val digitStyle = grid.digitStyle
            val noteStyle = cellTextStyle(noteSlot * NOTE_FRACTION)
            Column(Modifier.fillMaxSize()) {
                for (row in 0 until model.size) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until model.size) {
                            SudokuCellView(
                                cell = model.cells[row * model.size + col],
                                model = model,
                                digitStyle = digitStyle,
                                noteStyle = noteStyle,
                                onSelect = onSelect,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
        if (spotlight != null || overlay != null) SpotlightLayer(spotlight, overlay, model.size, strokes.box)
    }
}

/**
 * The layer over the base (lines included), in the cells' box: the [spotlight] measured once in the grid's digit style,
 * and the caller's [overlay], which is handed that same measuring to apply to Spotlights of its own.
 */
@Composable
private fun BoxScope.SpotlightLayer(spotlight: Spotlight?, overlay: (@Composable BoxScope.(restyle: (Spotlight) -> Spotlight) -> Unit)?, size: Int, frame: Dp) {
    BoxWithConstraints(Modifier.matchParentSize().padding(frame)) {
        val digits = rememberGridDigits(min(maxWidth, maxHeight), size)
        val restyle = remember(digits) { { lit: Spotlight -> lit.withDigitStyle(digits.measurer, digits.style, digits.cellPx) } }
        if (spotlight != null) {
            val shown = remember(spotlight, restyle) { restyle(spotlight) }
            SpotlightOverlay(shown, Modifier.testTag(SPOTLIGHT_TAG))
        }
        overlay?.invoke(this, restyle)
    }
}

/** What a Spotlight needs to draw Sudoku digits where the grid draws them: the [measurer], the cell's digit [style], and the cell pitch [cellPx]. */
private class GridDigits(val measurer: TextMeasurer, val style: TextStyle, val cellPx: Float)

/**
 * The digit look of a grid of [size] cells a side in a square [side] wide. The grid's Spotlight layer reads it, so a
 * glyph sits exactly where the grid draws that digit.
 */
@Composable
private fun rememberGridDigits(side: Dp, size: Int): GridDigits {
    val grid = gridMetrics(side, size)
    val cellPx = with(LocalDensity.current) { grid.cell.toPx() }
    val measurer = rememberTextMeasurer()
    return remember(grid.digitStyle, cellPx, measurer) { GridDigits(measurer, grid.digitStyle, cellPx) }
}

/** The grid's cell pitch and digit style; the base cells and the Spotlight layer both read them, so they cannot drift. */
private class GridMetrics(val cell: Dp, val digitStyle: TextStyle)

/** The metrics of a grid of [size] cells a side in a square [side] wide. */
@Composable
private fun gridMetrics(side: Dp, size: Int): GridMetrics {
    val cell = side / size
    return GridMetrics(cell, cellTextStyle(cell * DIGIT_FRACTION))
}

/** The cell text style at [fontSize], one line box tall so it centres in its cell; digits use tabular figures. */
@Composable
private fun cellTextStyle(fontSize: Dp): TextStyle {
    val base = MaterialTheme.typography.titleLarge
    val sp = with(LocalDensity.current) { fontSize.toSp() }
    return base.copy(fontFeatureSettings = TABULAR_FIGURES, textAlign = TextAlign.Center, fontSize = sp, lineHeight = sp)
}

internal class GridStrokes(val cell: Dp, val box: Dp)

/**
 * The grid takes the shared weights at both sizes: the token gridline and the geometry's wall.
 */
internal fun gridStrokes(geometry: BoardGeometry): GridStrokes =
    GridStrokes(cell = BoardTokens.boardGridLineWeight, box = geometry.wallWeight)

/**
 * Lines sit on the cell edges inside the frame; the thin ones go first so box lines cover their ends. The frame's
 * corners and the box lines' ends are rounded; a round end runs to the frame's centre line, so its cap sits
 * inside the frame's own stroke.
 */
private fun DrawScope.drawGridLines(model: SudokuBoardModel, strokes: GridStrokes, cellLine: Color, boxLine: Color) {
    val frame = strokes.box.toPx()
    val step = (size.width - 2 * frame) / model.size
    for (thick in listOf(false, true)) {
        val width = if (thick) frame else strokes.cell.toPx()
        val color = if (thick) boxLine else cellLine
        val from = if (thick) frame / 2 else frame
        val to = size.width - from
        val cap = if (thick) StrokeCap.Round else StrokeCap.Butt
        for (i in 1 until model.size) {
            val at = frame + i * step
            if ((i % model.boxCols == 0) == thick) drawLine(color, Offset(at, from), Offset(at, to), width, cap)
            if ((i % model.boxRows == 0) == thick) drawLine(color, Offset(from, at), Offset(to, at), width, cap)
        }
    }
    val outline = Stroke(frame, join = StrokeJoin.Round)
    drawRect(boxLine, Offset(frame / 2, frame / 2), Size(size.width - frame, size.height - frame), style = outline)
}

private const val GRID_TAG = "sudoku.grid"
private const val SPOTLIGHT_TAG = "sudoku.spotlight"
private const val TABULAR_FIGURES = "tnum"
private const val DIGIT_FRACTION = 0.6f
private const val NOTE_FRACTION = 0.8f
