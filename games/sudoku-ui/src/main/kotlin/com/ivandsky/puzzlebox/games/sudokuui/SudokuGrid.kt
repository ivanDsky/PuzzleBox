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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.games.sudokuui.hint.withDigitStyle

/**
 * The N x N board inside the square [modifier] gives it. The outer frame and box borders share
 * the thick stroke, cell borders the thin one; all lines draw over the cell fills, and the thin
 * ones take the board gridline role so they hold up on every highlight tint. Text scales with the
 * cell so any size the caller picks stays legible; digits use tabular figures.
 *
 * [spotlight] draws in a sibling layer over the cells and the lines, inside the same frame padding, so its cells are
 * the grid's cells; its digits are measured in the grid's digit style. It takes no taps, so a tap on a lit or dimmed
 * cell still selects it. Null composes nothing.
 */
@Composable
fun SudokuGrid(model: SudokuBoardModel, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, spotlight: Spotlight? = null) {
    val strokes = gridStrokes(model.size)
    val colors = MaterialTheme.colorScheme
    val cellLine = LocalBoardColors.current.boardGridLine
    val boxLine = colors.onSurface
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
            val cell = min(maxWidth, maxHeight) / model.size
            val noteSlot = min(cell / model.boxRows, cell / model.boxCols)
            val digitStyle = cellTextStyle(cell * DIGIT_FRACTION)
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
        if (spotlight != null) SpotlightLayer(spotlight, model.size, strokes.box)
    }
}

/** The overlay over the base (lines included), in the cells' box; measures the Sudoku digits once per Spotlight. */
@Composable
private fun BoxScope.SpotlightLayer(spotlight: Spotlight, size: Int, frame: Dp) {
    BoxWithConstraints(Modifier.matchParentSize().padding(frame)) {
        val cell = min(maxWidth, maxHeight) / size
        val digitStyle = cellTextStyle(cell * DIGIT_FRACTION)
        val cellPx = with(LocalDensity.current) { cell.toPx() }
        val measurer = rememberTextMeasurer()
        val shown = remember(spotlight, digitStyle, cellPx, measurer) { spotlight.withDigitStyle(measurer, digitStyle, cellPx) }
        SpotlightOverlay(shown, Modifier.testTag(SPOTLIGHT_TAG))
    }
}

/** The cell text style at [fontSize], one line box tall so it centres in its cell; digits use tabular figures. */
@Composable
private fun cellTextStyle(fontSize: Dp): TextStyle {
    val base = MaterialTheme.typography.titleLarge
    val sp = with(LocalDensity.current) { fontSize.toSp() }
    return base.copy(fontFeatureSettings = TABULAR_FIGURES, textAlign = TextAlign.Center, fontSize = sp, lineHeight = sp)
}

internal class GridStrokes(val cell: Dp, val box: Dp)

/** The 9x9 has more lines in the same square, so its box stroke is lighter than the 6x6's. */
internal fun gridStrokes(size: Int): GridStrokes =
    if (size > MINI_SIZE) GridStrokes(cell = 0.75.dp, box = 2.dp) else GridStrokes(cell = 1.dp, box = 3.dp)

/** Lines sit on the cell edges inside the frame; the thin ones go first so box lines cover their ends. */
private fun DrawScope.drawGridLines(model: SudokuBoardModel, strokes: GridStrokes, cellLine: Color, boxLine: Color) {
    val frame = strokes.box.toPx()
    val step = (size.width - 2 * frame) / model.size
    val far = size.width - frame
    for (thick in listOf(false, true)) {
        val width = if (thick) frame else strokes.cell.toPx()
        val color = if (thick) boxLine else cellLine
        for (i in 1 until model.size) {
            val at = frame + i * step
            if ((i % model.boxCols == 0) == thick) drawLine(color, Offset(at, frame), Offset(at, far), width)
            if ((i % model.boxRows == 0) == thick) drawLine(color, Offset(frame, at), Offset(far, at), width)
        }
    }
    drawRect(boxLine, Offset(frame / 2, frame / 2), Size(size.width - frame, size.height - frame), style = Stroke(frame))
}

private const val GRID_TAG = "sudoku.grid"
private const val SPOTLIGHT_TAG = "sudoku.spotlight"
private const val TABULAR_FIGURES = "tnum"
private const val MINI_SIZE = 6
private const val DIGIT_FRACTION = 0.6f
private const val NOTE_FRACTION = 0.8f
