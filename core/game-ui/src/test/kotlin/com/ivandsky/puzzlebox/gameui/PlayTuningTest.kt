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
    fun `ShippedPlayTuningSource always holds Starting`() {
        assertEquals(PlayTuning.Starting, ShippedPlayTuningSource.tuning.value)
    }
}
