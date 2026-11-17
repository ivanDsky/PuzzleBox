package com.ivandsky.puzzlebox.play

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Games tabs building blocks: the count rule. */
class GamesTabsTest {

    @Test
    fun tabCountTextCapsAtNineAndHidesZero() {
        assertEquals(null, tabCountText(0))
        assertEquals("1", tabCountText(1))
        assertEquals("9", tabCountText(9))
        assertEquals("9+", tabCountText(10))
    }
}
