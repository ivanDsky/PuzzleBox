package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {
    @Test
    fun `zero and negatives read 0 colon 00`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-5_000))
    }

    @Test
    fun `under a minute has no leading minute pad`() {
        assertEquals("0:58", formatDuration(58_000))
        assertEquals("0:59", formatDuration(59_999))
    }

    @Test
    fun `minutes boundaries`() {
        assertEquals("1:00", formatDuration(60_000))
        assertEquals("10:00", formatDuration(600_000))
        assertEquals("59:59", formatDuration(3_599_999))
    }

    @Test
    fun `hours appear from one hour`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }
}
