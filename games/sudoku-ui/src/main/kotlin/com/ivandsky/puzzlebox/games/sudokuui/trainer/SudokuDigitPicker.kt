package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.PopupPositionProvider
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.games.sudokuui.SudokuDigitKey
import com.ivandsky.puzzlebox.games.sudokuui.SudokuSizing
import kotlin.math.roundToInt

/** Test tag of the digit picker's row. */
internal const val SUDOKU_PICKER_TAG = "sudoku.trainer.picker"

/** Test tag of the picker's key for [digit]. */
internal fun sudokuPickerKeyTag(digit: Int): String = "sudoku.trainer.key.$digit"

/** The picker's lift above the page, so it reads as over the round and not part of it. */
private val PICKER_ELEVATION = 6.dp

/**
 * The digits the player may pick for the open cell, as one row of the play screen's own digit keys: a digit key per
 * entry of [digits] (a board of [size] cells a side), each the touch-target size, none carrying a count (the picker
 * tallies nothing). It sits in a popup over the round, outside the grid square, so it never hides a cell the player
 * needs to read (see [SudokuPickerPosition]).
 */
@Composable
internal fun SudokuDigitPicker(digits: List<Int>, size: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = PICKER_ELEVATION,
    ) {
        BoxWithConstraints {
            // A window narrower than the keys at their touch-target size (a 320 dp phone with a full row) narrows them.
            val fit = (maxWidth - Spacing.sm * 2 - Spacing.xs * (digits.size - 1)) / digits.size
            val keyWidth = min(SudokuSizing.keyMin, fit)
            Row(
                Modifier.padding(Spacing.sm).testTag(SUDOKU_PICKER_TAG),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                for (digit in digits) {
                    SudokuDigitKey(
                        digit = digit,
                        size = size,
                        remaining = 1,
                        notesMode = false,
                        reducedMotion = reducedMotion,
                        staggerOrder = 0,
                        onClick = { onPick(digit) },
                        modifier = Modifier.size(keyWidth, SudokuSizing.keyMin).testTag(sudokuPickerKeyTag(digit)),
                        counted = false,
                    )
                }
            }
        }
    }
}

/**
 * Where the picker for the cell [idx] of a [size]-wide board sits: centred under that cell's column and below the
 * board, or above it when the window has no room below (a cell in the bottom half is the usual case). The anchor is
 * the board's square (the popup is composed inside it), so the picker is never over the grid. Sideways it stays within
 * the board's own width when it fits there, else within the window; in a window with room neither below nor above it
 * is pressed against the bottom edge.
 */
internal class SudokuPickerPosition(private val idx: Int, private val size: Int, private val gap: Int) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val width = popupContentSize.width
        val fitsBoard = width <= anchorBounds.width
        val low = if (fitsBoard) anchorBounds.left else 0
        val high = if (fitsBoard) anchorBounds.right - width else maxOf(0, windowSize.width - width)
        val centre = anchorBounds.left + (idx % size + 0.5f) * anchorBounds.width / size
        val x = (centre - width / 2f).roundToInt().coerceIn(low, maxOf(low, high))
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height -> below
            above >= 0 -> above
            else -> maxOf(0, windowSize.height - popupContentSize.height)
        }
        return IntOffset(x, y)
    }
}
