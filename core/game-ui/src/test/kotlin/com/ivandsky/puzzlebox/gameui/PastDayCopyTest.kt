package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.PastDayStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** A20: the one past-day copy History's day header and the hub picker headline share. */
class PastDayCopyTest {

    @Test
    fun `the headline is the game and its number, or just the game when unnumbered`() {
        assertEquals("Zip #592", pastDayHeadline("Zip", 592))
        assertEquals("Strands", pastDayHeadline("Strands", null))
    }

    @Test
    fun `each status reads its own words`() {
        assertEquals("Solved 1:35", pastDayStatusLine(PastDayStatus.Solved(95_000)))
        assertEquals("In progress", pastDayStatusLine(PastDayStatus.InProgress(42_000)))
        assertEquals("Saved on this device", pastDayStatusLine(PastDayStatus.Stored))
        assertEquals("Needs a connection the first time", pastDayStatusLine(PastDayStatus.NeedsConnection))
    }

    @Test
    fun `each status has one action`() {
        assertEquals("Play again", pastDayAction(PastDayStatus.Solved(95_000)))
        assertEquals("Keep going", pastDayAction(PastDayStatus.InProgress(42_000)))
        assertEquals("Play", pastDayAction(PastDayStatus.Stored))
        assertEquals("Play", pastDayAction(PastDayStatus.NeedsConnection))
    }
}
