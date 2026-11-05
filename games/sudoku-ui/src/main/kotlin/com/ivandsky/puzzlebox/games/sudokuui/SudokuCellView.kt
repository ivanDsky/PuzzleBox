package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * One cell: its highlight tint, the digit or the notes mini-grid, and the conflict dot. The
 * whole cell is one TalkBack node; the drawn digits carry no semantics of their own so the
 * label is read once. Tints switch instantly (spec A11): no colour animation.
 */
@Composable
internal fun SudokuCellView(
    cell: SudokuCellModel,
    model: SudokuBoardModel,
    digitStyle: TextStyle,
    noteStyle: TextStyle,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = sudokuCellLabel(cell, model.size)
    val error = colors.error
    Box(
        modifier
            .background(cellTint(cell, colors))
            // The selection tint is the tap feedback; a ripple would animate a highlight.
            .clickable(interactionSource = null, indication = null) { onSelect(cell.idx) }
            .clearAndSetSemantics {
                testTag = "sudoku.cell.${cell.idx}"
                contentDescription = label
                collectionItemInfo = CollectionItemInfo(cell.idx / model.size, 1, cell.idx % model.size, 1)
                selected = cell.selected
                onClick(label = SELECT_ACTION) {
                    onSelect(cell.idx)
                    true
                }
            }
            .drawWithContent {
                drawContent()
                // Colour is never the only conflict signal: the dot marks the cell for colour-blind play.
                if (cell.conflict) {
                    val radius = size.minDimension * CONFLICT_DOT_FRACTION
                    drawCircle(error, radius, Offset(size.width - radius * 2, radius * 2))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            cell.digit != 0 -> Text(
                text = cell.digit.toString(),
                style = digitStyle,
                fontWeight = if (cell.given) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    cell.conflict -> colors.error
                    cell.given -> colors.onSurface
                    else -> colors.primary
                },
            )
            cell.notes != 0 -> NotesGrid(cell.notes, model, noteStyle, colors.onSurfaceVariant)
        }
    }
}

/** Digit `d` always sits in slot `d - 1` of a boxRows x boxCols grid, so notes never shift. */
@Composable
private fun NotesGrid(mask: Int, model: SudokuBoardModel, style: TextStyle, color: Color) {
    Column(Modifier.fillMaxSize()) {
        repeat(model.boxRows) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(model.boxCols) { col ->
                    val digit = row * model.boxCols + col + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (mask and (1 shl (digit - 1)) != 0) Text(digit.toString(), style = style, color = color)
                    }
                }
            }
        }
    }
}

/** Selection wins, then the hint target, same digit, hint unit and peers. */
private fun cellTint(cell: SudokuCellModel, colors: ColorScheme): Color = when {
    cell.selected -> colors.primaryContainer
    cell.hintTarget -> colors.tertiaryContainer
    cell.sameDigit -> colors.secondaryContainer
    cell.hintUnit -> colors.tertiaryContainer.copy(alpha = HINT_UNIT_ALPHA)
    cell.peer -> colors.surfaceVariant
    else -> colors.surface
}

/** TalkBack's line (spec A14): "Row 3, column 5, given 7", "…, 4", "…, notes 1 2 5", "…, empty". */
internal fun sudokuCellLabel(cell: SudokuCellModel, size: Int): String = buildString {
    append("Row ").append(cell.idx / size + 1).append(", column ").append(cell.idx % size + 1).append(", ")
    when {
        cell.given -> append("given ").append(cell.digit)
        cell.digit != 0 -> append(cell.digit)
        cell.notes != 0 -> append("notes ").append((1..size).filter { cell.notes and (1 shl (it - 1)) != 0 }.joinToString(" "))
        else -> append("empty")
    }
    if (cell.conflict) append(", conflict")
}

private const val SELECT_ACTION = "Select"
private const val CONFLICT_DOT_FRACTION = 0.07f
private const val HINT_UNIT_ALPHA = 0.5f
