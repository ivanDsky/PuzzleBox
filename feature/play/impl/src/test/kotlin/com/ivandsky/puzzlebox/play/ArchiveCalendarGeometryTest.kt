package com.ivandsky.puzzlebox.play

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Today's bar keeps 3dp from its own selected ring above it and the next row's ring below (06-a §Month calendar). */
class ArchiveCalendarGeometryTest {

    private val minClearance = 3.dp

    @Test
    fun `a phone row is 62dp around its 42dp disc`() = assertRowClears(compact = true, disc = 42.dp, cell = 62.dp)

    @Test
    fun `a tablet row is 66dp around its 46dp disc`() = assertRowClears(compact = false, disc = 46.dp, cell = 66.dp)

    private fun assertRowClears(compact: Boolean, disc: Dp, cell: Dp) {
        val rowDisc = archiveDiscFor(compact)
        val rowCell = archiveCellFor(rowDisc)
        assertEquals(disc, rowDisc)
        assertEquals(cell, rowCell)

        val ringBottom = rowCell / 2 + rowDisc / 2 + SelectedRingRoom
        val barTop = todayBarTop(rowCell, rowDisc)
        val barBottom = barTop + TodayBarHeight
        val nextRingTop = rowCell + rowCell / 2 - rowDisc / 2 - SelectedRingRoom

        val below = barTop - ringBottom
        val above = nextRingTop - barBottom
        assertTrue("bar $below below today's ring", below >= minClearance)
        assertTrue("bar $above above the next row's ring", above >= minClearance)
    }
}
