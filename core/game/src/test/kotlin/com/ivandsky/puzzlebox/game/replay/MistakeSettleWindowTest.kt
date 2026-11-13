package com.ivandsky.puzzlebox.game.replay

import org.junit.Assert.assertEquals
import org.junit.Test

/** The settle window is a value repeated from the design system: a change to either side must be seen. */
class MistakeSettleWindowTest {

    @Test
    fun `the mistake settle window is 500 ms`() {
        assertEquals(500L, Timeline.MISTAKE_SETTLE_MS)
    }
}
