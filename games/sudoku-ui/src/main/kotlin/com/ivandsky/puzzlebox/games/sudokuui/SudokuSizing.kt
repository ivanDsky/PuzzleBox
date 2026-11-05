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
 * Stacked (SinglePane), the grid sits above the keypad (the Notes/Erase row, then the
 * digit rows), so the board gets the height left after the keypad at 48dp keys; keys
 * grow toward [keyMax] only into height the board does not use — the board shrinks,
 * never the keys. Beside (TwoPane), the keypad lives in the side slot and the board
 * sizes against the full pane height. Either way the board is capped at
 * [PlayBoardMaxWidth] and is a whole multiple of a whole-dp cell.
 */
object SudokuSizing {

    /** The touch-target floor (§9): every key is at least this wide and tall. */
    internal val keyMin: Dp = 48.dp

    /** Roomy panes grow keys to this, so a tablet keypad does not read as a phone's. */
    internal val keyMax: Dp = 64.dp

    internal val keyGap: Dp = Spacing.sm

    // AdaptivePlayScaffold's TwoPane weights (board 0.62, side 0.38) and PlayChrome's
    // horizontal padding: only the board pane is measured, so the side slot's keypad
    // width is derived from it.
    private const val sideToBoardPaneRatio = 0.38f / 0.62f
    private val sidePanePadding: Dp = Spacing.lg * 2

    fun layout(maxWidth: Dp, maxHeight: Dp, size: Int, keypadBeside: Boolean): SudokuLayout {
        require(size > 0) { "size must be positive, was $size" }
        val keypadWidth = if (keypadBeside) maxWidth * sideToBoardPaneRatio - sidePanePadding else maxWidth
        val keyRows = if (rowWidthAtKeyMin(size) <= keypadWidth) 1 else 2
        val keysPerRow = (size + keyRows - 1) / keyRows
        val keyWidthShare = wholeDp((keypadWidth - keyGap * (keysPerRow - 1)) / keysPerRow)

        if (keypadBeside) {
            val cell = cellFor(minOf(maxWidth, maxHeight), size)
            val key = keyWidthShare.coerceIn(keyMin, keyMax)
            return SudokuLayout(cell, cell * size, key, keyGap, keyRows)
        }

        val keypadRows = keyRows + 1
        val keypadAtKeyMin = keypadHeight(keyMin, keypadRows)
        val cell = cellFor(minOf(maxWidth, maxHeight - keypadAtKeyMin), size)
        val board = cell * size
        val spareHeight = (maxHeight - keypadAtKeyMin - board).coerceAtLeast(0.dp)
        val key = minOf(keyWidthShare, keyMax, keyMin + wholeDp(spareHeight / keypadRows)).coerceAtLeast(keyMin)
        return SudokuLayout(cell, board, key, keyGap, keyRows)
    }

    /** Height a stacked keypad takes below the grid: per row (action row plus digit rows) one key and the gap above it. */
    internal fun stackedKeypadHeight(layout: SudokuLayout): Dp = keypadHeight(layout.key, layout.keyRows + 1)

    private fun keypadHeight(key: Dp, rows: Int): Dp = (key + keyGap) * rows

    private fun rowWidthAtKeyMin(keys: Int): Dp = keyMin * keys + keyGap * (keys - 1)

    private fun cellFor(bound: Dp, size: Int): Dp = wholeDp(minOf(bound, PlayBoardMaxWidth) / size).coerceAtLeast(0.dp)

    private fun wholeDp(dp: Dp): Dp = floor(dp.value).dp
}
