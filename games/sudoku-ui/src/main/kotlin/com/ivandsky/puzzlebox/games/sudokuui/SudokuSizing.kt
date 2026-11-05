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
 * Keys are at least 48dp in both width and height. The digits take one row when `size`
 * keys of 48dp plus gaps fit the keypad width, otherwise they wrap into two rows.
 *
 * The grid sits above the keypad (the action rows, then the digit rows) in every layout,
 * so the board gets the height left after the keypad at 48dp keys; keys grow toward
 * [keyMax] only into height the board does not use — the board shrinks, never the keys.
 * [actionRows] is 1 (Notes/Erase/Clear) or 2 when the Undo/Redo/Hint row joins it. The
 * board is capped at [PlayBoardMaxWidth] and is a whole multiple of a whole-dp cell.
 */
object SudokuSizing {

    /** The touch-target floor (§9): every key is at least this wide and tall. */
    internal val keyMin: Dp = 48.dp

    /** Roomy panes grow keys to this, so a tablet keypad does not read as a phone's. */
    internal val keyMax: Dp = 64.dp

    internal val keyGap: Dp = Spacing.sm

    fun layout(maxWidth: Dp, maxHeight: Dp, size: Int, actionRows: Int = 1): SudokuLayout {
        require(size > 0) { "size must be positive, was $size" }
        val keyRows = if (rowWidthAtKeyMin(size) <= maxWidth) 1 else 2
        val keysPerRow = (size + keyRows - 1) / keyRows
        val keyWidthShare = wholeDp((maxWidth - keyGap * (keysPerRow - 1)) / keysPerRow)

        val keypadRows = keyRows + actionRows
        val keypadAtKeyMin = keypadHeight(keyMin, keypadRows)
        val cell = cellFor(minOf(maxWidth, maxHeight - keypadAtKeyMin), size)
        val board = cell * size
        val spareHeight = (maxHeight - keypadAtKeyMin - board).coerceAtLeast(0.dp)
        val key = minOf(keyWidthShare, keyMax, keyMin + wholeDp(spareHeight / keypadRows)).coerceAtLeast(keyMin)
        return SudokuLayout(cell, board, key, keyGap, keyRows, actionRows)
    }

    /** Height a stacked keypad takes below the grid: per row (action rows plus digit rows) one key and the gap above it. */
    internal fun stackedKeypadHeight(layout: SudokuLayout): Dp = keypadHeight(layout.key, layout.keyRows + layout.actionRows)

    private fun keypadHeight(key: Dp, rows: Int): Dp = (key + keyGap) * rows

    private fun rowWidthAtKeyMin(keys: Int): Dp = keyMin * keys + keyGap * (keys - 1)

    private fun cellFor(bound: Dp, size: Int): Dp = wholeDp(minOf(bound, PlayBoardMaxWidth) / size).coerceAtLeast(0.dp)

    private fun wholeDp(dp: Dp): Dp = floor(dp.value).dp
}
