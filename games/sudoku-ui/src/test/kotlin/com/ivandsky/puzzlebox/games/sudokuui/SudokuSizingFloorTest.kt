package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Under a play tuning the keys hold the touch floor and the grid gives way first, and Clear has
// left the keypad (the Corner pad sizes its strip and full-width digits). Panes are SudokuSizingTest's: 336x386 small phone, 388x661 tall phone, 552x1000 tablet.
class SudokuSizingFloorTest {

    private val smallPhone = 336.dp to 386.dp
    private val tallPhone = 388.dp to 661.dp
    private val tabletPane = 552.dp to 1000.dp

    private fun floored(pane: Pair<Dp, Dp>, size: Int, inline: Boolean = false) =
        SudokuSizing.layout(pane.first, pane.second, size, inline, keyFloor = SudokuSizing.keyMin)

    @Test
    fun `no floor is today's layout`() {
        for (pane in listOf(smallPhone, tallPhone, tabletPane)) for (size in listOf(6, 9)) {
            assertEquals(
                SudokuSizing.layout(pane.first, pane.second, size),
                SudokuSizing.layout(pane.first, pane.second, size, keyFloor = 0.dp),
            )
        }
    }

    @Test
    fun `the floor holds every key at 48dp where the cell would drag it under`() {
        // 6x6 on a small phone: the cell is 46dp, so today's key is 47dp; the floor lifts it.
        val six = floored(smallPhone, 6)
        assertTrue("key ${six.key}", six.key >= SudokuSizing.keyMin)
        assertTrue("action key ${six.actionKey}", six.actionKey >= SudokuSizing.keyMin)
        // 9x9 on a small phone: 30dp cells and keys today.
        val nine = floored(smallPhone, 9)
        assertTrue("key ${nine.key}", nine.key >= SudokuSizing.keyMin)
        assertTrue("action key ${nine.actionKey}", nine.actionKey >= SudokuSizing.keyMin)
    }

    @Test
    fun `the grid shrinks first and still fits the pane`() {
        val plain = SudokuSizing.layout(smallPhone.first, smallPhone.second, 9)
        val nine = floored(smallPhone, 9)
        assertTrue("grid ${nine.board} not under today's ${plain.board}", nine.board < plain.board)
        val used = nine.board + nine.keyGap + SudokuSizing.keypadHeight(nine)
        assertTrue("grid + keypad $used overflows ${smallPhone.second}: $nine", used <= smallPhone.second)
    }

    @Test
    fun `every pane keeps keys at the floor and the board inside the pane`() {
        val widths = listOf(320, 336, 360, 388, 411, 600, 800, 1000)
        val heights = listOf(386, 480, 640, 661, 891, 1000, 1280)
        for (size in listOf(6, 9)) for (inline in listOf(false, true)) for (w in widths) for (h in heights) {
            val layout = floored(w.dp to h.dp, size, inline)
            val case = "size=$size inline=$inline ${w}x$h $layout"
            assertTrue(case, layout.key >= SudokuSizing.keyMin && layout.actionKey >= SudokuSizing.keyMin)
            assertTrue(case, layout.board <= w.dp)
            assertEquals(case, layout.cell * size, layout.board)
        }
    }

    @Test
    fun `a tall pane is untouched by the floor`() {
        assertEquals(
            SudokuSizing.layout(tabletPane.first, tabletPane.second, 9),
            floored(tabletPane, 9),
        )
    }

    @Test
    fun `the corner pad gives the digits the full width and a 56dp strip, in every pane`() {
        for (size in listOf(6, 9)) for (w in listOf(320, 336, 388, 600, 1000)) for (h in listOf(386, 661, 1000)) {
            val layout = SudokuSizing.layout(w.dp, h.dp, size, keyFloor = SudokuSizing.keyMin, corner = true)
            val case = "size=$size ${w}x$h $layout"
            assertEquals(case, SudokuKeypadActions.Corner, layout.actions)
            assertEquals(case, SudokuSizing.cornerStrip, layout.actionKey)
            val digits = SudokuSizing.digitKeyWidth(w.dp, layout.keyRows, layout.actions)
            assertEquals(case, (w.dp - SudokuSizing.keyGap * 2) / 3, digits)
            val digitRows = layout.key * layout.keyRows + SudokuSizing.keyGap * (layout.keyRows - 1)
            assertEquals(case, digitRows + SudokuSizing.keyGap + SudokuSizing.cornerStrip, SudokuSizing.keypadHeight(layout))
        }
    }

    @Test
    fun `the grid gives way before the corner pad's keys drop under the floor`() {
        val layout = SudokuSizing.layout(smallPhone.first, smallPhone.second, 9, keyFloor = SudokuSizing.keyMin, corner = true)
        assertTrue("key ${layout.key}", layout.key >= SudokuSizing.keyMin)
        val used = layout.board + layout.keyGap + SudokuSizing.keypadHeight(layout)
        assertTrue("grid + pad $used overflows ${smallPhone.second}: $layout", used <= smallPhone.second)
    }
}
