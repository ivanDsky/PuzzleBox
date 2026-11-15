package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PanePolicyTest {
    private fun twoPane(w: Int, h: Int) = PanePolicy.isTwoPane(w.dp, h.dp)

    @Test
    fun `phone landscape stays single pane`() {
        assertEquals(false, twoPane(891, 448))
        assertEquals(false, twoPane(960, 479))
    }

    @Test
    fun `tablet landscape is two pane`() {
        assertEquals(true, twoPane(1280, 800))
        assertEquals(true, twoPane(840, 480))
    }

    @Test
    fun `tablet portrait stays single pane`() {
        assertEquals(false, twoPane(800, 1280))
        assertEquals(false, twoPane(840, 1280))
    }

    @Test
    fun `medium landscape stays single pane`() {
        assertEquals(false, twoPane(839, 600))
    }
}
