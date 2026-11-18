package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardCapsTest {
    @Test
    fun `a phone is capped at 552dp whatever the grid`() {
        assertEquals(552.dp, boardCapFor(DeviceClass.Phone, null))
        assertEquals(552.dp, boardCapFor(DeviceClass.Phone, 6))
        assertEquals(552.dp, boardCapFor(DeviceClass.Phone, 12))
    }

    @Test
    fun `a tablet class is capped by 96dp a column when that is below 640dp`() {
        assertEquals(576.dp, boardCapFor(DeviceClass.TabletPortrait, 6))
        assertEquals(576.dp, boardCapFor(DeviceClass.TabletLandscape, 6))
    }

    @Test
    fun `a tablet class keeps 640dp when 96dp a column is more`() {
        assertEquals(640.dp, boardCapFor(DeviceClass.TabletPortrait, 9))
        assertEquals(640.dp, boardCapFor(DeviceClass.TabletLandscape, 12))
    }

    @Test
    fun `a tablet class with no grid columns keeps 640dp`() {
        assertEquals(640.dp, boardCapFor(DeviceClass.TabletPortrait, null))
        assertEquals(640.dp, boardCapFor(DeviceClass.TabletLandscape, null))
    }
}
