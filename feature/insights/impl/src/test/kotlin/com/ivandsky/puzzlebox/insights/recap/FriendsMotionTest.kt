package com.ivandsky.puzzlebox.insights.recap

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendsMotionTest {

    @Test
    fun `the chip springs in 760ms after the crowd has gathered, however many friends`() {
        // A = min(friends x 24, 480) + 520: a small crowd must not make the chip wait for a large crowd's gather.
        assertEquals(9 * 24 + 520 + 760, FriendsMotion.chipAtMs(9))
        assertEquals(480 + 520 + 760, FriendsMotion.chipAtMs(30))
        assertEquals(1_760, FriendsMotion.chipAtMs(20))
    }

    @Test
    fun `friends step into the crowd min(24, 480 per friend) ms apart so the last one lands by the cap`() {
        assertEquals(24, FriendsMotion.gatherStepMs(9))
        assertEquals(16, FriendsMotion.gatherStepMs(30))
        assertEquals(480, 30 * FriendsMotion.gatherStepMs(30))
    }
}
