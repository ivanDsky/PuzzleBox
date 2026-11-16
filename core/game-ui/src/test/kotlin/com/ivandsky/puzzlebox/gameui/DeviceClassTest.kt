package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClassTest {
    private fun classFor(w: Int, h: Int) = deviceClassFor(w.dp, h.dp)

    @Test
    fun `phone portrait is Phone`() {
        assertEquals(DeviceClass.Phone, classFor(360, 800))
    }

    @Test
    fun `phone landscape stays Phone`() {
        assertEquals(DeviceClass.Phone, classFor(891, 448))
        assertEquals(DeviceClass.Phone, classFor(800, 360))
    }

    @Test
    fun `tablet portrait is TabletPortrait`() {
        assertEquals(DeviceClass.TabletPortrait, classFor(800, 1280))
        assertEquals(DeviceClass.TabletPortrait, classFor(600, 960))
    }

    @Test
    fun `700dp wide tablet landscape is a single pane TabletPortrait`() {
        assertEquals(DeviceClass.TabletPortrait, classFor(700, 620))
    }

    @Test
    fun `two pane window is TabletLandscape`() {
        assertEquals(DeviceClass.TabletLandscape, classFor(1280, 800))
        assertEquals(DeviceClass.TabletLandscape, classFor(840, 480))
    }

    @Test
    fun `a window the pane policy makes two pane is always TabletLandscape`() {
        for ((w, h) in listOf(840 to 480, 960 to 600, 1280 to 800, 1920 to 1080)) {
            assertEquals(true, PanePolicy.isTwoPane(w.dp, h.dp))
            assertEquals(DeviceClass.TabletLandscape, classFor(w, h))
        }
    }

    @Test
    fun `shortest side just under 600dp is Phone`() {
        assertEquals(DeviceClass.Phone, classFor(599, 900))
        assertEquals(DeviceClass.Phone, classFor(700, 599))
    }
}
