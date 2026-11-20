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
    fun `a full tablet page stops at the 96dp cap and steps the text up`() {
        val page = layout(800, 500)

        assertEquals(96f, page.ring.value, EPS)
        assertTrue(page.streak.text > layout(390, 250).streak.text)
        assertTrue(page.nameSize > layout(390, 250).nameSize)
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
        val used = layout.verticalPadding * 2 + layout.streak.line + layout.ring + WidgetLayout.BUTTON_HEIGHT

        assertTrue(used <= 110.dp)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
