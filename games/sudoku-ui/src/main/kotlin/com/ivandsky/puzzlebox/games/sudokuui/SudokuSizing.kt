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
 * The grid sits above the keypad in every layout, so the board gets the height left after
 * the keypad at 48dp keys; keys grow toward [keyMax] only into height the board does not
 * use — the board shrinks, never the keys. The keypad is the Notes/Erase/Clear row over
 * the digit rows, or, with [inlineNotes] (TwoPane, where Erase and Clear sit in the side
 * pane), the digit rows alone with the notes toggle as one more key after the digits.
 * Those keys are narrow (ten across the capped column), so their height stays within
 * [INLINE_KEY_ASPECT] of their width. The board is capped at [PlayBoardMaxWidth] and is
 * a whole multiple of a whole-dp cell.
 */
object SudokuSizing {

    /** The touch-target floor (§9): every key is at least this wide and tall. */
    internal val keyMin: Dp = 48.dp

    /** Roomy panes grow keys to this, so a tablet keypad does not read as a phone's. */
    internal val keyMax: Dp = 64.dp

    internal val keyGap: Dp = Spacing.sm

    /** Between digit keys in a row, tighter than [keyGap] so nine keys keep their width. */
    internal val digitGap: Dp = Spacing.xs

    /** Between the last digit and the inline notes toggle, so the toggle reads as its own key. */
    internal val toggleBreak: Dp = Spacing.sm

    fun layout(maxWidth: Dp, maxHeight: Dp, size: Int, inlineNotes: Boolean = false): SudokuLayout {
        require(size > 0) { "size must be positive, was $size" }
        val keys = if (inlineNotes) size + 1 else size
        val keyRows = if (rowWidthAtKeyMin(keys, inlineNotes) <= maxWidth) 1 else 2
        val keysPerRow = (keys + keyRows - 1) / keyRows
        val keyWidthShare = if (inlineNotes) {
            wholeDp(inlineKeyWidth(minOf(maxWidth, PlayBoardMaxWidth), keysPerRow) * INLINE_KEY_ASPECT)
        } else {
            wholeDp((maxWidth - keyGap * (keysPerRow - 1)) / keysPerRow)
        }

        val keypadRows = keyRows + actionRows(inlineNotes)
        val keypadAtKeyMin = keypadHeight(keyMin, keypadRows)
        val cell = cellFor(minOf(maxWidth, maxHeight - keypadAtKeyMin), size)
        val board = cell * size
        val spareHeight = (maxHeight - keypadAtKeyMin - board).coerceAtLeast(0.dp)
        val key = minOf(keyWidthShare, keyMax, keyMin + wholeDp(spareHeight / keypadRows)).coerceAtLeast(keyMin)
        return SudokuLayout(cell, board, key, keyGap, keyRows, inlineNotes)
    }

    /** Height a stacked keypad takes below the grid: per row (action row, if any, plus digit rows) one key and the gap above it. */
    internal fun stackedKeypadHeight(layout: SudokuLayout): Dp =
        keypadHeight(layout.key, layout.keyRows + actionRows(layout.inlineNotes))

    /** One key's width in a [keysPerRow] row of [width] that ends in the inline notes toggle. */
    internal fun inlineKeyWidth(width: Dp, keysPerRow: Int): Dp =
        (width - digitGap * (keysPerRow - 2) - toggleBreak) / keysPerRow

    private fun actionRows(inlineNotes: Boolean): Int = if (inlineNotes) 0 else 1

    private fun keypadHeight(key: Dp, rows: Int): Dp = (key + keyGap) * rows

    private fun rowWidthAtKeyMin(keys: Int, inlineNotes: Boolean): Dp =
        if (inlineNotes) keyMin * keys + digitGap * (keys - 2) + toggleBreak else keyMin * keys + keyGap * (keys - 1)

    private fun cellFor(bound: Dp, size: Int): Dp = wholeDp(minOf(bound, PlayBoardMaxWidth) / size).coerceAtLeast(0.dp)

    private fun wholeDp(dp: Dp): Dp = floor(dp.value).dp

    /** Inline keys are at most this much taller than wide (UX notes-key design: 50x56 at 9x9). */
    private const val INLINE_KEY_ASPECT = 1.1f
}
