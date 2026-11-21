package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStepTest {
    private fun step(width: Float, height: Float) = WidgetStep.of(DpSize(width.dp, height.dp))

    @Test
    fun `under 160dp tall is chips1 and 160dp is chips2`() {
        assertEquals(WidgetStep.Chips1, step(276f, 159.99f))
        assertEquals(WidgetStep.Chips2, step(276f, 160f))
    }

    @Test
    fun `under 280dp tall is chips2 and 280dp is a list`() {
        assertEquals(WidgetStep.Chips2, step(340f, 279.99f))
        assertEquals(WidgetStep.ListNarrow, step(340f, 280f))
    }

    @Test
    fun `a list needs 340dp of width, so a narrower tall widget is chips2 however tall`() {
        assertEquals(WidgetStep.Chips2, step(339.99f, 280f))
        assertEquals(WidgetStep.Chips2, step(250f, 600f))
        assertEquals(WidgetStep.Chips2, step(200f, 900f))
        assertEquals(WidgetStep.ListNarrow, step(340f, 600f))
    }

    @Test
    fun `a list under 480dp wide is narrow and 480dp is wide`() {
        assertEquals(WidgetStep.ListNarrow, step(479.99f, 400f))
        assertEquals(WidgetStep.ListWide, step(480f, 400f))
    }

    @Test
    fun `height decides first, so a wide short widget stays a chip row`() {
        assertEquals(WidgetStep.Chips1, step(620f, 90f))
        assertEquals(WidgetStep.Chips2, step(620f, 220f))
    }

    @Test
    fun `the S9 Ultra placement of about 620 by 430dp is the wide list`() {
        assertEquals(WidgetStep.ListWide, step(620f, 430f))
    }

    @Test
    fun `the smallest sizes the provider allows are chips1`() {
        assertEquals(WidgetStep.Chips1, step(250f, 90f))
    }
}
