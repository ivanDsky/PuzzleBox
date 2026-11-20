package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    private fun layout(width: Int, height: Int) = WidgetLayout.of(DpSize(width.dp, height.dp))

    @Test
    fun `4 by 2 keeps the 28dp rings, no names and the full width button`() {
        val layout = layout(250, 110)

        assertEquals(28f, layout.ring.value, EPS)
        assertEquals(14f, layout.glyph.value, EPS)
        assertFalse(layout.showNames)
        assertTrue(layout.compact)
    }

    @Test
    fun `5 by 3 draws the 56dp rings with names and a pill button`() {
        val layout = layout(320, 180)

        assertTrue(layout.ring >= WidgetLayout.NAMES_FROM)
        assertTrue(layout.showNames)
        assertFalse(layout.compact)
    }

    @Test
    fun `a 6 by 4 tablet size grows the rings and the streak line with the width`() {
        val five = layout(320, 180)
        val six = layout(390, 250)

        assertEquals(71.2f, six.ring.value, EPS)
        assertTrue(six.ring > five.ring)
        assertTrue(six.streak.text >= five.streak.text)
        assertTrue(six.showNames)
        assertFalse(six.compact)
    }

    @Test
    fun `4 by 2 keeps the 48dp button and the compact text sizes`() {
        val layout = layout(250, 110)

        assertEquals(48f, layout.button.height.value, EPS)
        assertEquals(24f, layout.streak.line.value, EPS)
        assertEquals(16f, layout.streak.text.value, EPS)
    }

    @Test
    fun `5 by 3 keeps the 48dp button and the first step of text`() {
        val layout = layout(320, 180)

        assertEquals(57.2f, layout.ring.value, EPS)
        assertEquals(48f, layout.button.height.value, 0.5f)
        assertEquals(26f, layout.streak.line.value, 0.5f)
        assertEquals(12f, layout.nameSize.value, 0.5f)
    }

    @Test
    fun `650 by 450 rings take the width share and the chrome grows with them`() {
        val layout = layout(650, 450)

        assertEquals(123.2f, layout.ring.value, EPS)
        assertTrue(layout.streak.text.value > 22f)
        assertTrue(layout.nameSize.value > 14f)
        assertTrue(layout.button.height.value in 48f..64f)
        assertTrue(layout.button.height > WidgetLayout.BUTTON_HEIGHT)
        assertEmptyBandsUnderOneRing(layout, 450)
    }

    @Test
    fun `900 by 600 rings reach the 160dp ceiling and the button stops at 64dp`() {
        val layout = layout(900, 600)

        assertEquals(160f, layout.ring.value, EPS)
        assertTrue(layout.button.height.value in 48f..64f)
        assertTrue(layout.streak.text > layout(650, 450).streak.text)
        assertEmptyBandsUnderOneRing(layout, 600)
    }

    @Test
    fun `the block fills the height with its gaps instead of one big margin`() {
        val layout = layout(650, 450)

        assertEquals(448f, layout.height.value, 0.1f)
        assertTrue(layout.blockGap > layout(320, 180).blockGap)
    }

    @Test
    fun `chrome never shrinks as the widget grows`() {
        val sizes = listOf(320 to 180, 390 to 250, 650 to 450, 800 to 500, 900 to 600).map { (w, h) -> layout(w, h) }

        sizes.zipWithNext { small, big ->
            assertTrue(big.ring >= small.ring)
            assertTrue(big.streak.line >= small.streak.line)
            assertTrue(big.streak.text >= small.streak.text)
            assertTrue(big.nameSize >= small.nameSize)
            assertTrue(big.button.height >= small.button.height)
            assertTrue(big.button.text >= small.button.text)
        }
    }

    @Test
    fun `a layout never draws taller than the widget it was given`() {
        listOf(250 to 110, 250 to 90, 320 to 180, 390 to 250, 650 to 450, 800 to 500, 900 to 600, 600 to 110)
            .forEach { (w, h) -> assertTrue("$w x $h", layout(w, h).height.value <= h + EPS || layout(w, h).compact) }
    }

    @Test
    fun `a short widget keeps the 28dp floor instead of squeezing the rings`() {
        val layout = layout(250, 90)

        assertEquals(28f, layout.ring.value, EPS)
        assertTrue(layout.compact)
    }

    @Test
    fun `a wide but short widget stays compact, not a pill over rings that do not fit`() {
        val layout = layout(600, 110)

        assertTrue(layout.compact)
        assertFalse(layout.showNames)
    }

    @Test
    fun `the four by two content fits the 110dp it is given`() {
        val layout = layout(250, 110)

        assertTrue(layout.height <= 110.dp)
    }

    private fun assertEmptyBandsUnderOneRing(layout: WidgetLayout, height: Int) {
        val band = (height.dp - layout.height) / 2 + layout.verticalPadding

        assertTrue("band $band", band <= layout.ring)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
