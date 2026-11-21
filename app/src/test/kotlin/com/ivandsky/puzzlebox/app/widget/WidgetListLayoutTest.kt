package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The list steps; the chip steps are WidgetLayoutTest.
class WidgetListLayoutTest {
    private fun layout(width: Int, height: Int) = WidgetLayout.of(DpSize(width.dp, height.dp))

    @Test
    fun `the narrow list at 4 by 3, 420 by 280dp, has 14 by 10dp padding, a 36dp ring, the short streak and 48dp rows`() {
        val layout = layout(420, 280)

        assertEquals(WidgetStep.ListNarrow, layout.step)
        assertEquals(14f, layout.look.horizontalPadding.value, EPS)
        assertEquals(10f, layout.look.verticalPadding.value, EPS)
        assertEquals(36f, layout.look.ring.value, EPS)
        assertEquals(17f, layout.look.streakText.value, EPS)
        assertEquals(19f, layout.look.flame.value, EPS)
        assertFalse(layout.look.longStreak)
        assertFalse(layout.look.timeLine)
        assertTrue(layout.look.pill)
        assertEquals(48f, layout.rowHeight.value, EPS)
        assertEquals(260f, layout.contentHeight.value, EPS)
    }

    @Test
    fun `the wide list at 4 by 3, 480 by 320dp, has a 40dp ring, a 22dp flame, the long streak and 48dp rows`() {
        val layout = layout(480, 320)

        assertEquals(WidgetStep.ListWide, layout.step)
        assertEquals(14f, layout.look.horizontalPadding.value, EPS)
        assertEquals(10f, layout.look.verticalPadding.value, EPS)
        assertEquals(40f, layout.look.ring.value, EPS)
        assertEquals(20f, layout.look.streakText.value, EPS)
        assertEquals(22f, layout.look.flame.value, EPS)
        assertTrue(layout.look.longStreak)
        assertEquals(48f, layout.rowHeight.value, EPS)
        assertEquals(300f, layout.contentHeight.value, EPS)
    }

    @Test
    fun `a list whose bucket holds its header, five rows and the pill has the whole height for its block`() {
        val layout = layout(340, 338)

        assertEquals(WidgetStep.ListNarrow, layout.step)
        assertEquals(48f, layout.rowHeight.value, EPS)
        assertEquals(318f, layout.contentHeight.value, EPS)
    }

    @Test
    fun `the wide list below the growth sizes has a 22dp flame, the long streak and a 40dp ring`() {
        val layout = layout(559, 399)

        assertEquals(WidgetStep.ListWide, layout.step)
        assertEquals(22f, layout.look.flame.value, EPS)
        assertEquals(40f, layout.look.ring.value, EPS)
        assertEquals(48f, layout.rowHeight.value, EPS)
        assertTrue(layout.look.longStreak)
    }

    @Test
    fun `the 4 by 3 buckets, 280 and 320 tall, keep their rows and block height, and a taller bucket shares its height among the rows`() {
        for (width in listOf(420, 480)) {
            assertTrue("$width x 280", layout(width, 280).fixedRows)
            assertTrue("$width x 320", layout(width, 320).fixedRows)
            assertFalse("$width x 360", layout(width, 360).fixedRows)
        }
        assertTrue("340 x 280", layout(340, 280).fixedRows)
        assertFalse("340 x 360", layout(340, 360).fixedRows)
    }

    @Test
    fun `a 560 by 400dp bucket has a bigger ring, taller rows and bigger text than 4 by 3, and 700 by 500 bigger again`() {
        val base = layout(470, 300)
        val up1 = layout(560, 400)
        val up2 = layout(720, 540)

        assertTrue(up1.rowHeight > base.rowHeight)
        assertTrue(up2.rowHeight > up1.rowHeight)
        assertTrue(up1.look.ring > base.look.ring)
        assertTrue(up2.look.ring > up1.look.ring)
        assertTrue(up1.look.streakText.value > base.look.streakText.value)
        assertTrue(up2.look.streakText.value > up1.look.streakText.value)
        assertTrue(up1.look.text(16).value > base.look.text(16).value)
        assertTrue(up2.look.text(16).value > up1.look.text(16).value)
        assertTrue(up1.look.size(116.dp) > base.look.size(116.dp))
    }

    @Test
    fun `every list bucket that is not fixed holds its header, five rows and pill, so its rows are never squeezed below their size`() {
        WidgetSizes.ALL.map { WidgetLayout.of(it) }
            .filter { (it.step == WidgetStep.ListNarrow || it.step == WidgetStep.ListWide) && !it.fixedRows }
            .forEach { assertTrue("$it", WidgetLayout.listHeight(it.look, it.rowHeight) <= it.contentHeight) }
    }

    private companion object {
        const val EPS = 0.01f
    }
}
