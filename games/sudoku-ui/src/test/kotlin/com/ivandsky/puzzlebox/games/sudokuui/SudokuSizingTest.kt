package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

// A15 sizing policy — computed, never eyeballed (QueensSizingTest precedent). The panes are
// the board areas SudokuPlayUiTest measures (screen less chrome and gutters), as prototype 01 D.
class SudokuSizingTest {

    private val smallPhone = 336.dp to 386.dp // 360x640
    private val tallPhone = 388.dp to 661.dp // 412x915
    private val tabletPane = 552.dp to 1000.dp

    private fun layout(pane: Pair<Dp, Dp>, size: Int, inlineNotes: Boolean = false): SudokuLayout =
        SudokuSizing.layout(pane.first, pane.second, size, inlineNotes)

    private fun assertFits(layout: SudokuLayout, pane: Pair<Dp, Dp>) {
        val used = layout.board + layout.keyGap + SudokuSizing.keypadHeight(layout)
        assertTrue("grid + keypad $used overflows ${pane.second}: $layout", used <= pane.second)
        assertTrue("board ${layout.board} wider than ${pane.first}", layout.board <= pane.first)
    }

    private fun assertBalanced(layout: SudokuLayout) {
        assertTrue("key ${layout.key} under cell ${layout.cell}", layout.key >= layout.cell)
        assertTrue("key ${layout.key} over 1.25 x cell ${layout.cell}", layout.key <= layout.cell * 1.25f)
    }

    @Test
    fun `digits sit in three columns - 6x6 in two rows, 9x9 in three`() {
        for (pane in listOf(smallPhone, tallPhone, tabletPane)) for (inline in listOf(false, true)) {
            assertEquals(2, layout(pane, 6, inline).keyRows)
            assertEquals(3, layout(pane, 9, inline).keyRows)
        }
    }

    @Test
    fun `tall phone keeps the action row under a width-bound board and grows keys into spare height`() {
        val nine = layout(tallPhone, 9)
        assertEquals(
            SudokuLayout(cell = 43.dp, board = 387.dp, key = 53.dp, actionKey = 48.dp, keyGap = 8.dp, keyRows = 3, actions = SudokuKeypadActions.Row),
            nine,
        )
        assertEquals(124.dp, SudokuSizing.digitKeyWidth(tallPhone.first, nine.keyRows, nine.actions))
        assertBalanced(nine)
        assertFits(nine, tallPhone)

        val six = layout(tallPhone, 6)
        assertEquals(
            SudokuLayout(cell = 64.dp, board = 384.dp, key = 80.dp, actionKey = 60.dp, keyGap = 8.dp, keyRows = 2, actions = SudokuKeypadActions.Row),
            six,
        )
        assertBalanced(six)
        assertFits(six, tallPhone)
    }

    @Test
    fun `tablet pane caps the board and grows keys to 1_25x the cell`() {
        val nine = layout(tabletPane, 9)
        assertEquals(
            SudokuLayout(cell = 61.dp, board = 549.dp, key = 76.dp, actionKey = 57.dp, keyGap = 8.dp, keyRows = 3, actions = SudokuKeypadActions.Row),
            nine,
        )
        assertEquals(178.67f, SudokuSizing.digitKeyWidth(minOf(tabletPane.first, PlayBoardMaxWidth), nine.keyRows, nine.actions).value, 0.01f)
        assertBalanced(nine)
        assertFits(nine, tabletPane)

        val six = layout(tabletPane, 6)
        assertEquals(PlayBoardMaxWidth, six.board)
        assertEquals(SudokuKeypadActions.Row, six.actions)
        assertBalanced(six)
        assertFits(six, tabletPane)
    }

    @Test
    fun `small phone moves notes, erase and clear into a fourth column so cell and key stay level`() {
        val nine = layout(smallPhone, 9)
        assertEquals(
            SudokuLayout(cell = 30.dp, board = 270.dp, key = 30.dp, actionKey = 30.dp, keyGap = 8.dp, keyRows = 3, actions = SudokuKeypadActions.Column),
            nine,
        )
        assertBalanced(nine)
        assertFits(nine, smallPhone)
        // The action row would have cost the board five dp a cell (25dp cells, 27dp keys).
        assertTrue(nine.cell > 25.dp)

        val six = layout(smallPhone, 6)
        assertEquals(
            SudokuLayout(cell = 46.dp, board = 276.dp, key = 47.dp, actionKey = 47.dp, keyGap = 8.dp, keyRows = 2, actions = SudokuKeypadActions.Column),
            six,
        )
        assertBalanced(six)
        assertFits(six, smallPhone)
        // 6x6 row 2 splits Erase | Clear, so the column is wide enough for two 48dp halves.
        assertEquals(104.dp, SudokuSizing.actionColumnWidth(smallPhone.first, six.keyRows, six.actions))
    }

    @Test
    fun `two pane puts the notes toggle in a fourth column beside the digits`() {
        // Tab S9 landscape, 1280x800, TwoPane: 62% of the width less the gutters, 800 less the gutters.
        val pane = (1280.dp * 0.62f - 24.dp) to (800.dp - 24.dp)
        val nine = layout(pane, 9, inlineNotes = true)
        assertEquals(
            SudokuLayout(cell = 61.dp, board = 549.dp, key = 67.dp, actionKey = 67.dp, keyGap = 8.dp, keyRows = 3, actions = SudokuKeypadActions.NotesColumn),
            nine,
        )
        assertBalanced(nine)
        assertFits(nine, pane)
        assertFits(layout(pane, 6, inlineNotes = true), pane)
    }

    @Test
    fun `every pane keeps the board a whole multiple of a whole-dp cell, balanced with the keys, inside the pane`() {
        val widths = listOf(200, 320, 336, 360, 388, 411, 600, 800, 1000, 1280)
        val heights = listOf(300, 386, 480, 640, 661, 891, 1000, 1280)
        for (size in listOf(6, 9)) for (inline in listOf(false, true)) for (w in widths) for (h in heights) {
            val pane = w.dp to h.dp
            val layout = layout(pane, size, inline)
            val case = "size=$size inline=$inline ${w}x$h $layout"
            assertTrue(case, layout.board <= PlayBoardMaxWidth)
            assertEquals(case, layout.cell * size, layout.board)
            assertEquals(case, floor(layout.cell.value), layout.cell.value, 0f)
            assertTrue(case, layout.key >= layout.cell && layout.key <= layout.cell * 1.25f)
            assertTrue(case, layout.actionKey >= 48.dp || layout.actions != SudokuKeypadActions.Row)
            assertFits(layout, pane)
            if (inline) assertEquals(case, SudokuKeypadActions.NotesColumn, layout.actions)
        }
    }
}
