package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// §6 sizing policy — computed, never eyeballed (HomeLayoutPolicyTest precedent).
class QueensSizingTest {

    @Test
    fun `compact phone bounds the board by width`() {
        // 10x10 on a 360dp pane: width-bound, above the gesture floor.
        val side = queensBoardSideFor(
            paneWidth = 360.dp, paneHeight = 640.dp,
            reservedTop = 56.dp, reservedBottom = 56.dp, cellsPerSide = 10,
        )
        assertEquals(328.dp, side) // 360 − 2×16 padding
        assertTrue(side >= QueensBoardDefaults.cellMinSize * 10)
    }

    @Test
    fun `expanded pane caps cells at 64dp`() {
        // S9-Ultra detail pane: 6x6 would balloon — the cap holds.
        val side = queensBoardSideFor(
            paneWidth = 1000.dp, paneHeight = 1200.dp,
            reservedTop = 56.dp, reservedBottom = 56.dp, cellsPerSide = 6,
        )
        assertEquals(QueensBoardDefaults.cellMaxSize * 6, side)
    }

    @Test
    fun `height binds when the pane is short`() {
        val side = queensBoardSideFor(
            paneWidth = 800.dp, paneHeight = 500.dp,
            reservedTop = 100.dp, reservedBottom = 100.dp, cellsPerSide = 8,
        )
        assertEquals(500 - 100 - 100 - 32, side.value.toInt())
    }

    @Test
    fun `floor holds when the pane is tiny`() {
        val side = queensBoardSideFor(
            paneWidth = 200.dp, paneHeight = 200.dp,
            reservedTop = 0.dp, reservedBottom = 0.dp, cellsPerSide = 4,
        )
        assertEquals(168.dp, side)
    }
}
