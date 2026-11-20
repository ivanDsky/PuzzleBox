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
    fun `chips1 has 10 by 8dp padding, a 32dp ring, the short streak and no pill or time line`() {
        val layout = layout(276, 102)

        assertEquals(WidgetStep.Chips1, layout.step)
        assertEquals(10f, layout.look.horizontalPadding.value, EPS)
        assertEquals(8f, layout.look.verticalPadding.value, EPS)
        assertEquals(32f, layout.look.ring.value, EPS)
        assertFalse(layout.look.longStreak)
        assertFalse(layout.look.timeLine)
        assertFalse(layout.look.pill)
    }

    @Test
    fun `chips2 has 14dp padding, a 36dp ring, the time line and the pill`() {
        val layout = layout(276, 220)

        assertEquals(WidgetStep.Chips2, layout.step)
        assertEquals(14f, layout.look.horizontalPadding.value, EPS)
        assertEquals(14f, layout.look.verticalPadding.value, EPS)
        assertEquals(36f, layout.look.ring.value, EPS)
        assertTrue(layout.look.timeLine)
        assertTrue(layout.look.pill)
    }

    @Test
    fun `chips2 says days in a row only from 320dp of content, so the freezes and the done count are never squeezed out`() {
        assertFalse(layout(276, 220).look.longStreak)
        assertFalse(layout(347, 220).look.longStreak)
        assertTrue(layout(348, 220).look.longStreak)
        assertTrue(layout(620, 430).look.longStreak)
        assertFalse(layout(900, 102).look.longStreak)
    }

    @Test
    fun `the block is the widget less its padding`() {
        assertEquals(256f, layout(276, 102).contentWidth.value, EPS)
        assertEquals(86f, layout(276, 102).contentHeight.value, EPS)
        assertEquals(248f, layout(276, 220).contentWidth.value, EPS)
        assertEquals(192f, layout(276, 220).contentHeight.value, EPS)
    }

    @Test
    fun `the block stops at 440dp wide however wide the widget is`() {
        assertEquals(440f, layout(500, 102).contentWidth.value, EPS)
        assertEquals(440f, layout(900, 220).contentWidth.value, EPS)
    }

    @Test
    fun `a taller widget does not stretch the block past the top of chips2`() {
        assertEquals(251f, layout(276, 279).contentHeight.value, EPS)
        assertEquals(252f, layout(620, 430).contentHeight.value, EPS)
        assertEquals(252f, layout(900, 900).contentHeight.value, EPS)
    }

    @Test
    fun `both list steps draw the chips2 look centred at its widest`() {
        listOf(layout(276, 338), layout(620, 430)).forEach {
            assertTrue(it.look.pill)
            assertTrue(it.look.timeLine)
            assertEquals(14f, it.look.verticalPadding.value, EPS)
        }
        assertEquals(WidgetStep.ListNarrow, layout(276, 338).step)
        assertEquals(WidgetStep.ListWide, layout(620, 430).step)
        assertEquals(440f, layout(620, 430).contentWidth.value, EPS)
    }

    @Test
    fun `the 250 by 90dp minimum fits its chips1 block`() {
        val layout = layout(250, 90)

        assertEquals(74f, layout.contentHeight.value, EPS)
        assertEquals(230f, layout.contentWidth.value, EPS)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
