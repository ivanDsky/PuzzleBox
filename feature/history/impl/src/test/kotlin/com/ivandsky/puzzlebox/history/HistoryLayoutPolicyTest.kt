package com.ivandsky.puzzlebox.history

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ticket 07 policy, applied to history's list-detail: width AND orientation decide. */
class HistoryLayoutPolicyTest {

    @Test
    fun `expanded landscape is two-pane`() {
        assertEquals(
            HistoryLayout.TwoPane,
            historyLayoutFor(WindowWidthSizeClass.Expanded, isLandscape = true),
        )
    }

    @Test
    fun `expanded portrait is single-pane`() {
        assertEquals(
            HistoryLayout.SinglePane,
            historyLayoutFor(WindowWidthSizeClass.Expanded, isLandscape = false),
        )
    }

    @Test
    fun `medium is single-pane in either orientation`() {
        assertEquals(
            HistoryLayout.SinglePane,
            historyLayoutFor(WindowWidthSizeClass.Medium, isLandscape = false),
        )
        assertEquals(
            HistoryLayout.SinglePane,
            historyLayoutFor(WindowWidthSizeClass.Medium, isLandscape = true),
        )
    }

    @Test
    fun `compact is single-pane in either orientation`() {
        assertEquals(
            HistoryLayout.SinglePane,
            historyLayoutFor(WindowWidthSizeClass.Compact, isLandscape = false),
        )
        assertEquals(
            HistoryLayout.SinglePane,
            historyLayoutFor(WindowWidthSizeClass.Compact, isLandscape = true),
        )
    }
}
