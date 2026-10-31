package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatClockTest {

    @Test
    fun `formats minutes and seconds zero padded`() {
        assertEquals("00:00", formatClock(0))
        assertEquals("00:07", formatClock(7_000))
        assertEquals("01:00", formatClock(60_000))
        assertEquals("12:05", formatClock(725_000))
    }

    @Test
    fun `subsecond time floors to seconds`() {
        assertEquals("00:59", formatClock(59_999))
    }
}

/** A3: hints are counted by the caller; the controller just keeps the count. */
class HintControllerTest {

    @Test
    fun `starts at zero and counts every recorded hint`() {
        val hints = HintController()
        assertEquals(0, hints.usedCount)
        hints.recordHint()
        hints.recordHint()
        assertEquals(2, hints.usedCount)
    }

    @Test
    fun `resumes from a restored count`() {
        assertEquals(3, HintController(initialUsed = 3).usedCount)
    }
}
