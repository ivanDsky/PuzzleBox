package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayTuningTest {
    @Test
    fun `Starting caps every single pane class at 552dp`() {
        assertEquals(552.dp, PlayTuning.Starting.boardCapFor(DeviceClass.Phone))
        assertEquals(552.dp, PlayTuning.Starting.boardCapFor(DeviceClass.TabletPortrait))
    }

    @Test
    fun `Starting lets the board fill its pane in tablet landscape`() {
        assertNull(PlayTuning.Starting.boardCapFor(DeviceClass.TabletLandscape))
    }

    @Test
    fun `Starting keeps landscape controls under the board`() {
        assertEquals(LandscapeControls.UnderBoard, PlayTuning.Starting.landscapeControls)
    }

    @Test
    fun `boardCapFor reads the cap of its own class`() {
        val tuning = PlayTuning(400.dp, 500.dp, 600.dp, LandscapeControls.SideColumn)
        assertEquals(400.dp, tuning.boardCapFor(DeviceClass.Phone))
        assertEquals(500.dp, tuning.boardCapFor(DeviceClass.TabletPortrait))
        assertEquals(600.dp, tuning.boardCapFor(DeviceClass.TabletLandscape))
    }

    @Test
    fun `Starting has no tablet cell cap`() {
        assertNull(PlayTuning.Starting.tabletCellCap)
    }

    @Test
    fun `a tablet class is capped by its cell cap times the columns when that is smaller`() {
        val tuning = PlayTuning(552.dp, 600.dp, 600.dp, LandscapeControls.SideColumn, tabletCellCap = 72.dp)

        assertEquals(432.dp, tuning.boardCapForGrid(DeviceClass.TabletPortrait, 6))
        assertEquals(432.dp, tuning.boardCapForGrid(DeviceClass.TabletLandscape, 6))
    }

    @Test
    fun `a tablet class keeps its class cap when the cell cap times the columns is larger`() {
        val tuning = PlayTuning(552.dp, 600.dp, 600.dp, LandscapeControls.SideColumn, tabletCellCap = 72.dp)

        assertEquals(600.dp, tuning.boardCapForGrid(DeviceClass.TabletPortrait, 9))
        assertEquals(600.dp, tuning.boardCapForGrid(DeviceClass.TabletLandscape, 12))
    }

    @Test
    fun `the phone has no cell cap`() {
        val tuning = PlayTuning(552.dp, 600.dp, 600.dp, LandscapeControls.SideColumn, tabletCellCap = 40.dp)

        assertEquals(552.dp, tuning.boardCapForGrid(DeviceClass.Phone, 6))
    }

    @Test
    fun `no grid columns or no cell cap leaves the class cap`() {
        val capped = PlayTuning(552.dp, 600.dp, 600.dp, LandscapeControls.SideColumn, tabletCellCap = 72.dp)

        assertEquals(600.dp, capped.boardCapForGrid(DeviceClass.TabletPortrait, null))
        assertEquals(600.dp, capped.copy(tabletCellCap = null).boardCapForGrid(DeviceClass.TabletPortrait, 6))
        assertNull(PlayTuning.Starting.boardCapForGrid(DeviceClass.TabletLandscape, null))
    }

    @Test
    fun `a cell cap bounds a tablet class that has no class cap`() {
        val tuning = PlayTuning.Starting.copy(tabletCellCap = 64.dp)

        assertEquals(384.dp, tuning.boardCapForGrid(DeviceClass.TabletLandscape, 6))
    }

    @Test
    fun `Shipped holds the chosen values`() {
        assertEquals(
            PlayTuning(552.dp, 640.dp, 640.dp, LandscapeControls.SideColumn, tabletCellCap = 80.dp),
            PlayTuning.Shipped,
        )
    }

    @Test
    fun `ShippedPlayTuningSource emits Shipped`() {
        assertEquals(PlayTuning.Shipped, ShippedPlayTuningSource.tuning.value)
    }
}
