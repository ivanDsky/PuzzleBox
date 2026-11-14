package com.ivandsky.puzzlebox.gameui.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockPlacesTest {
    @Test
    fun `the docked row rolls a new place and counts a new time`() {
        assertTrue(DockPlaces.animated)
    }

    @Test
    fun `the docked row rolls at once, with no stagger`() {
        assertEquals(0, DockPlaces.delayMs)
    }

    @Test
    fun `the docked row flips no medal`() {
        assertFalse(DockPlaces.arrived)
    }
}
