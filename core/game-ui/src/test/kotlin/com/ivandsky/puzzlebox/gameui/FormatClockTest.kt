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
