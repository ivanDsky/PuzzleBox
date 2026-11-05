package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/**
 * The N x N board inside the square [modifier] gives it. The outer frame and box borders share
 * the thick stroke, cell borders the thin one; text scales with the cell so any size the caller
 * picks stays legible.
 */
@Composable
fun SudokuGrid(model: SudokuBoardModel, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val strokes = gridStrokes(model.size)
    val colors = MaterialTheme.colorScheme
    val cellLine = colors.outlineVariant
    val boxLine = colors.onSurface
    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .semantics { collectionInfo = CollectionInfo(model.size, model.size) }
            .testTag(GRID_TAG)
            .drawWithContent {
                drawContent()
                drawGridLines(model, strokes, cellLine, boxLine)
            }
            .padding(strokes.box),
    ) {
        val cell = min(maxWidth, maxHeight) / model.size
        val noteSlot = min(cell / model.boxRows, cell / model.boxCols)
        val density = LocalDensity.current
        val base = MaterialTheme.typography.titleLarge
        val digitStyle = with(density) {
            base.copy(fontSize = (cell * DIGIT_FRACTION).toSp(), lineHeight = (cell * DIGIT_FRACTION).toSp(), textAlign = TextAlign.Center)
        }
        val noteStyle = with(density) {
            base.copy(fontSize = (noteSlot * NOTE_FRACTION).toSp(), lineHeight = (noteSlot * NOTE_FRACTION).toSp(), textAlign = TextAlign.Center)
        }
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
}

private class GridStrokes(val cell: Dp, val box: Dp)

/** The 9x9 has more lines in the same square, so its box stroke is lighter than the 6x6's. */
private fun gridStrokes(size: Int): GridStrokes =
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
private const val MINI_SIZE = 6
private const val DIGIT_FRACTION = 0.6f
private const val NOTE_FRACTION = 0.8f
