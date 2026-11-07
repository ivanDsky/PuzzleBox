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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText

/**
 * One cell: its highlight tint, the digit or the notes mini-grid, the hint target's ring and the
 * conflict dot. The whole cell is one TalkBack node; the drawn digits carry no semantics of their
 * own so the label is read once. Highlights switch instantly (spec A11); the only motion is a
 * player commit's one-shot effect from [LocalSudokuMotion], read while drawing.
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
    val board = LocalBoardColors.current
    val entryInk = LocalPrimaryText.current
    val label = sudokuCellLabel(cell, model.size)
    val error = colors.error
    val ring = colors.onTertiaryContainer
    val motion = LocalSudokuMotion.current
    Box(
        modifier
            .background(cellTint(cell, colors, board))
            .waveTint(motion, cell.idx, colors.primary)
            .selectionTile(cell.selected, board)
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
                // The ring, not the fill alone, picks the target out of its tinted unit (V9).
                if (cell.hintTarget) {
                    val width = HINT_RING_WIDTH.toPx()
                    val inset = HINT_RING_INSET.toPx() + width / 2
                    drawRect(
                        color = ring,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(width),
                    )
                }
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
                    // The full teal fill needs the hue's deep ink; green entries and grey givens fail on it.
                    cell.selected -> board.sudokuSelectionInk
                    cell.given -> colors.onSurface
                    else -> entryInk
                },
                modifier = Modifier.popScale(motion, cell.idx),
            )
            cell.notes != 0 -> NotesGrid(
                cell.notes,
                model,
                noteStyle,
                if (cell.selected) board.sudokuSelectionInk else colors.onSurfaceVariant,
            )
        }
        val ghost = motion?.ghostDigitOf(cell.idx) ?: 0
        if (cell.digit == 0 && ghost != 0) {
            Text(
                text = ghost.toString(),
                style = digitStyle,
                color = entryInk,
                modifier = Modifier.graphicsLayer { alpha = motion?.ghostAlpha() ?: 0f },
            )
        }
    }
}

/**
 * The selection as an inset tile sitting on a lip of its own hue (spec A14): the face drawn
 * above the lip, both inside the cell, so no neighbour paints over it.
 */
private fun Modifier.selectionTile(selected: Boolean, board: BoardColors): Modifier =
    if (!selected) {
        this
    } else {
        drawBehind {
            val inset = SELECTION_TILE_INSET.toPx()
            val depth = SELECTION_LIP_DEPTH.toPx()
            val corner = CornerRadius(SELECTION_TILE_RADIUS.toPx())
            val face = Size(size.width - inset * 2, size.height - inset * 2 - depth)
            drawRoundRect(board.sudokuSelectionLip, Offset(inset, inset + depth), face, corner)
            drawRoundRect(board.sudokuSelection, Offset(inset, inset), face, corner)
        }
    }

private fun Modifier.waveTint(motion: SudokuMotionState?, idx: Int, color: Color): Modifier =
    if (motion == null) {
        this
    } else {
        drawBehind {
            val intensity = motion.tintOf(idx)
            if (intensity > 0f) drawRect(color, alpha = intensity * SudokuMotion.WAVE_ALPHA)
        }
    }

private fun Modifier.popScale(motion: SudokuMotionState?, idx: Int): Modifier =
    if (motion == null) {
        this
    } else {
        graphicsLayer {
            val scale = motion.scaleOf(idx)
            scaleX = scale
            scaleY = scale
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

/** Selection sits on the bare board (its tile draws over it), then the hint target, same digit, struck cells, hint unit and peers. */
private fun cellTint(cell: SudokuCellModel, colors: ColorScheme, board: BoardColors): Color = when {
    cell.selected -> colors.surface
    cell.hintTarget -> colors.tertiaryContainer
    // The selection's family, lighter: never the keypad's secondaryContainer fill (V7).
    cell.sameDigit -> board.sudokuSelection.copy(alpha = SAME_DIGIT_ALPHA)
    cell.hintStruck -> colors.onSurfaceVariant.copy(alpha = HINT_STRUCK_ALPHA)
    cell.hintUnit -> colors.tertiaryContainer.copy(alpha = HINT_UNIT_ALPHA)
    cell.peer -> board.sudokuPeer
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
private const val HINT_STRUCK_ALPHA = 0.22f
private const val SAME_DIGIT_ALPHA = 0.5f
private val HINT_RING_WIDTH = 2.dp
private val HINT_RING_INSET = 1.dp
private val SELECTION_TILE_INSET = 2.dp
private val SELECTION_TILE_RADIUS = 6.dp
private val SELECTION_LIP_DEPTH = 3.dp
