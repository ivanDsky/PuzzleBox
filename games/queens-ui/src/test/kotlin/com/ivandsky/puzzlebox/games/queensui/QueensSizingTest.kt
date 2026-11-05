package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
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
        assertEquals(344.dp, side) // 360 − 2×8 gutters (n ≥ 10)
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

    @Test
    fun `every size 4 to 12 fits a 360x640 pane`() {
        for (n in 4..12) {
            val side = queensBoardSideFor(
                paneWidth = 360.dp, paneHeight = 640.dp,
                reservedTop = 0.dp, reservedBottom = 0.dp, cellsPerSide = n,
            )
            assertTrue("n=$n side=$side exceeds the pane", side <= 360.dp)
            assertTrue("n=$n side=$side exceeds the cap", side <= queensBoardMaxSideFor(n))
        }
    }

    /** V1: a 4×4 lesson fills the tutorial's board pane like Tango's and Zip's lessons. */
    @Test
    fun `a lesson board fills its pane up to the shared board cap`() {
        fun lesson(width: Int, height: Int) = queensBoardSideFor(
            paneWidth = width.dp, paneHeight = height.dp,
            reservedTop = 0.dp, reservedBottom = 0.dp, cellsPerSide = 4, panePadding = 0.dp,
        )
        assertEquals(379.dp, lesson(379, 600)) // phone: the host's padded pane, edge to edge
        assertEquals(520.dp, lesson(520, 900)) // tablet: the host's 552dp column less its padding
        assertEquals(PlayBoardMaxWidth, lesson(1000, 1200))
    }

    @Test
    fun `every daily size keeps the 64dp cell cap`() {
        assertEquals(PlayBoardMaxWidth, queensBoardMaxSideFor(4))
        for (n in 5..12) assertEquals("n=$n", QueensBoardDefaults.cellMaxSize * n, queensBoardMaxSideFor(n))
        val side = queensBoardSideFor(
            paneWidth = 1000.dp, paneHeight = 1200.dp,
            reservedTop = 0.dp, reservedBottom = 0.dp, cellsPerSide = 5,
        )
        assertEquals(320.dp, side)
    }

    @Test
    fun `gutters shrink to 8dp from 10 cells`() {
        assertEquals(16.dp * 2, queensPanePaddingFor(9))
        assertEquals(8.dp * 2, queensPanePaddingFor(10))
        val side = queensBoardSideFor(
            paneWidth = 360.dp, paneHeight = 640.dp,
            reservedTop = 0.dp, reservedBottom = 0.dp, cellsPerSide = 12,
        )
        assertEquals(344.dp, side) // 360 − 2×8: pane-bound, below the 32dp floor yet never forced over
    }
}
