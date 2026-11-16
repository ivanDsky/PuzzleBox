package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A15/A6 sizing policy — pure, computed, never eyeballed. The caller passes the space its
 * board pane gives it (chrome and gutters already excluded, as Queens' board does).
 *
 * The grid sits above a 3-column numpad (size / 3 digit rows). One balanced rule sizes both
 * (prototype 01 D, user pick): the digit key is as tall as a board cell, and grows to at
 * most 1.25 x the cell only into height left over, so neither the board nor the keys win at
 * the other's cost; a pane too small for 48dp of both shrinks them together. The board
 * takes the width (capped at [boardMax]: [PlayBoardMaxWidth], or a two-pane board pane's
 * side; a whole multiple of a whole-dp cell) whenever the height allows.
 *
 * Notes, Erase and Clear sit in a row under the digits, [actionKeyFor] tall. When that row
 * would force the width-bound board smaller (a cramped pane: the key would have to drop
 * under the cell), they move into a fourth column beside the digits at key height instead
 * ([SudokuKeypadActions.Column]). With inlineNotes (TwoPane, where Erase and Clear sit in
 * the toolbar) only the notes toggle takes that column.
 *
 * Under a play tuning the caller passes a [keyFloor][layout] and sets [corner][layout]: the keys
 * then never shrink below the floor (the cell and the grid give way first), and the keypad is
 * [SudokuKeypadActions.Corner]: digits across the full width under a [cornerStrip]-tall strip
 * holding the corner Notes toggle and Erase, Clear having moved to the shared control row.
 */
object SudokuSizing {

    /** The touch-target floor (§9) the action row holds, and the width of each 6x6 Erase | Clear half. */
    internal val keyMin: Dp = 48.dp

    /** The Corner pad's strip: tall enough for Erase's icon over its label; the Notes toggle inside it stays [keyMin] square. */
    internal val cornerStrip: Dp = 56.dp

    /** Prototype 01 D's ceiling, so a roomy 6x6 pane does not turn keys into slabs. */
    internal val keyMax: Dp = 96.dp

    internal val keyGap: Dp = Spacing.sm

    private const val COLUMNS = 3
    private const val KEY_GROWTH = 1.25f
    private const val ACTION_ROW_SHARE = 0.75f

    fun layout(
        maxWidth: Dp,
        maxHeight: Dp,
        size: Int,
        inlineNotes: Boolean = false,
        boardMax: Dp = PlayBoardMaxWidth,
        keyFloor: Dp = 0.dp,
        corner: Boolean = false,
    ): SudokuLayout {
        require(size > 0 && size % COLUMNS == 0) { "size must be a positive multiple of $COLUMNS, was $size" }
        val rows = size / COLUMNS
        val widthCell = floor(minOf(maxWidth, boardMax).value / size).toInt().coerceAtLeast(0)
        val floorKey = keyFloor.value.toInt()
        // A key is as tall as its cell, but never under the floor.
        fun keyAt(cell: Int) = maxOf(cell, floorKey)
        val actions = when {
            corner -> SudokuKeypadActions.Corner
            inlineNotes -> SudokuKeypadActions.NotesColumn
            heightFor(size, rows, SudokuKeypadActions.Row, widthCell, keyAt(widthCell)) <= maxHeight -> SudokuKeypadActions.Row
            else -> SudokuKeypadActions.Column
        }
        var cell = widthCell
        while (cell > 0 && heightFor(size, rows, actions, cell, keyAt(cell)) > maxHeight) cell--
        var key = keyAt(cell)
        val keyCap = maxOf(minOf(floor(cell * KEY_GROWTH).toInt(), keyMax.value.toInt()), floorKey)
        while (key < keyCap && heightFor(size, rows, actions, cell, key + 1) <= maxHeight) key++
        val actionKey = actionKeyFor(actions, key.dp)
        return SudokuLayout(cell.dp, (cell * size).dp, key.dp, actionKey, keyGap, rows, actions)
    }

    /** The keypad's height under the grid's gap: the digit rows, plus the action row when it sits below. */
    internal fun keypadHeight(layout: SudokuLayout): Dp =
        keypadHeight(layout.keyRows, layout.actions, layout.key, layout.actionKey)

    /**
     * The fourth column's width in a keypad [width] wide; a 6x6 Column splits its second row
     * Erase | Clear, two 48dp halves.
     */
    internal fun actionColumnWidth(width: Dp, keyRows: Int, actions: SudokuKeypadActions): Dp {
        val quarter = (width - keyGap * COLUMNS) / (COLUMNS + 1)
        val splitsRow = actions == SudokuKeypadActions.Column && keyRows < COLUMNS
        return if (splitsRow) maxOf(quarter, keyMin * 2 + keyGap) else quarter
    }

    /** One digit key's width in a keypad [width] wide: a third of it, or of what the fourth column leaves. */
    internal fun digitKeyWidth(width: Dp, keyRows: Int, actions: SudokuKeypadActions): Dp {
        val digits = if (actions.digitsFillWidth) width else width - keyGap - actionColumnWidth(width, keyRows, actions)
        return (digits - keyGap * (COLUMNS - 1)) / COLUMNS
    }

    /** The action row trails the key at three quarters of its height, never under the touch floor. */
    private fun actionRowFor(key: Dp): Dp = maxOf(keyMin, (key.value * ACTION_ROW_SHARE).roundToInt().dp)

    /** [actions]' height: the row's, the Corner strip's (always [cornerStrip]), or the digits' own beside them. */
    private fun actionKeyFor(actions: SudokuKeypadActions, key: Dp): Dp = when (actions) {
        SudokuKeypadActions.Row -> actionRowFor(key)
        SudokuKeypadActions.Corner -> cornerStrip
        else -> key
    }

    private fun keypadHeight(rows: Int, actions: SudokuKeypadActions, key: Dp, actionKey: Dp): Dp {
        val digits = key * rows + keyGap * (rows - 1)
        return if (actions.digitsFillWidth) digits + keyGap + actionKey else digits
    }

    private fun heightFor(size: Int, rows: Int, actions: SudokuKeypadActions, cell: Int, key: Int): Dp =
        (cell * size).dp + keyGap + keypadHeight(rows, actions, key.dp, actionKeyFor(actions, key.dp))
}
