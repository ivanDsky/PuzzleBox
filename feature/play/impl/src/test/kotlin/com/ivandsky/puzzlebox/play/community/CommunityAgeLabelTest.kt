package com.ivandsky.puzzlebox.play.community

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityAgeLabelTest {

    @Test
    fun `an age reads in the largest whole unit`() {
        assertEquals("just now", ageLabel(NOW - 59_000, NOW))
        assertEquals("1 min ago", ageLabel(NOW - MINUTE, NOW))
        assertEquals("59 min ago", ageLabel(NOW - 59 * MINUTE, NOW))
        assertEquals("1 h ago", ageLabel(NOW - HOUR, NOW))
        assertEquals("23 h ago", ageLabel(NOW - 23 * HOUR - 59 * MINUTE, NOW))
        assertEquals("1 d ago", ageLabel(NOW - 24 * HOUR, NOW))
    }

    @Test
    fun `a puzzle dated after this device's clock is just now`() {
        assertEquals("just now", ageLabel(NOW + HOUR, NOW))
    }
}
