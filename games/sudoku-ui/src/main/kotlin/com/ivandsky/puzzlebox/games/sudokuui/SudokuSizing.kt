package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import kotlin.math.floor

/**
 * A15 sizing policy — pure, computed, never eyeballed. The caller passes the space its
 * board pane gives it (chrome and gutters already excluded, as Queens' board does).
 *
 * Stacked (SinglePane), the grid sits above the keypad: Notes/Erase over one row of
 * `size` digits, so the board gets the height left after two key rows and their gaps.
 * Beside (TwoPane), the keypad lives in the side slot and the board sizes against the
 * full pane height. Either way the board is capped at [PlayBoardMaxWidth] and is a whole
 * multiple of a whole-dp cell, so every cell edge lands on the same grid.
 */
object SudokuSizing {

    /** The touch-target floor (§9): every key is at least this tall. */
    internal val keyMin: Dp = 48.dp

    /** Roomy panes grow keys to this, so a tablet keypad does not read as a phone's. */
    internal val keyMax: Dp = 64.dp

    internal val keyGap: Dp = Spacing.sm

    fun layout(maxWidth: Dp, maxHeight: Dp, size: Int, keypadBeside: Boolean): SudokuLayout {
        require(size > 0) { "size must be positive, was $size" }
        val digitShare = (maxWidth - keyGap * (size - 1)) / size
        val key = floor(digitShare.value).dp.coerceIn(keyMin, keyMax)
        val heightForBoard = if (keypadBeside) maxHeight else maxHeight - stackedKeypadHeight(key)
        val bound = minOf(maxWidth, heightForBoard, PlayBoardMaxWidth)
        val cell = floor(bound.value / size).coerceAtLeast(0f).dp
        return SudokuLayout(cell = cell, board = cell * size, key = key, keyGap = keyGap)
    }

    /** Height a stacked keypad takes below the grid: the gap above it, two key rows and the gap between them. */
    internal fun stackedKeypadHeight(layout: SudokuLayout): Dp = stackedKeypadHeight(layout.key)

    private fun stackedKeypadHeight(key: Dp): Dp = key * 2 + keyGap * 2
}
