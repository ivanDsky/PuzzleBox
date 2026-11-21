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
    fun `chips2 on the tablet's 4 by 2, about 470 by 200dp, has a ring of at least 48dp with the larger chip text`() {
        val look = layout(470, 200).look

        assertTrue(look.ring.value >= 48f)
        assertEquals(16f, look.chipFirstText.value, EPS)
        assertEquals(13f, look.chipSecondText.value, EPS)
    }

    @Test
    fun `chips2 on a phone, about 250 by 160dp, keeps its 36dp ring and 14 and 12sp chip text`() {
        val look = layout(250, 160).look

        assertEquals(36f, look.ring.value, EPS)
        assertEquals(14f, look.chipFirstText.value, EPS)
        assertEquals(12f, look.chipSecondText.value, EPS)
    }

    @Test
    fun `the chips2 ring grows with the room, from 36 to 56dp, and the text steps up only above 44dp`() {
        assertEquals(36f, layout(340, 160).look.ring.value, EPS)
        assertEquals(56f, layout(540, 279).look.ring.value, EPS)
        for (height in 160..279) for (width in 250..540) {
            val look = layout(width, height).look
            assertTrue("$width x $height ring ${look.ring}", look.ring.value in 36f..56f)
            assertEquals("$width x $height", look.ring.value > 44f, look.chipFirstText.value == 16f)
        }
    }

    @Test
    fun `a chips2 block that grew its ring still fits its streak line, ring, chip text, pill and two gaps`() {
        for (height in 160..279) for (width in 250..540) {
            val layout = layout(width, height)
            if (layout.look.ring.value <= 36f) continue

            val needed = layout.look.streakHeight + layout.look.ring + WidgetLayout.chipTextHeight(layout.look) +
                WidgetLayout.PILL_HEIGHT + WidgetLayout.CHIP_GAP_MIN * 2
            assertTrue("$width x $height needs $needed of ${layout.contentHeight}", needed <= layout.contentHeight)
        }
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
    fun `chips2 never stretches its block, it ends one dp under the list threshold`() {
        assertEquals(251f, layout(276, 279).contentHeight.value, EPS)
        assertEquals(251f, layout(620, 279).contentHeight.value, EPS)
    }

    @Test
    fun `the narrow list has 14 by 10dp padding, a 36dp ring and the short streak, and its block is a header, five rows and the pill`() {
        val layout = layout(276, 338)

        assertEquals(WidgetStep.ListNarrow, layout.step)
        assertEquals(14f, layout.look.horizontalPadding.value, EPS)
        assertEquals(10f, layout.look.verticalPadding.value, EPS)
        assertEquals(36f, layout.look.ring.value, EPS)
        assertFalse(layout.look.longStreak)
        assertEquals(248f, layout.contentWidth.value, EPS)
        assertEquals(48f, layout.rowHeight.value, EPS)
        assertEquals(317f, layout.contentHeight.value, EPS)
    }

    @Test
    fun `a list row is the room left over five, floored, between 48 and 58dp`() {
        assertEquals(48f, layout(276, 338).rowHeight.value, EPS)
        assertEquals(56f, layout(276, 380).rowHeight.value, EPS)
        assertEquals(58f, layout(276, 456).rowHeight.value, EPS)
        assertEquals(58f, layout(900, 900).rowHeight.value, EPS)
        assertEquals(48f, layout(276, 280).rowHeight.value, EPS)
    }

    @Test
    fun `a taller widget does not stretch the list block past its 58dp rows, and the block never outgrows the widget`() {
        assertEquals(367f, layout(276, 456).contentHeight.value, EPS)
        assertEquals(367f, layout(276, 900).contentHeight.value, EPS)
        assertEquals(370f, layout(900, 900).contentHeight.value, EPS)
        assertEquals(260f, layout(276, 280).contentHeight.value, EPS)
    }

    @Test
    fun `the narrow list shows New best from 340dp of widget, five cells and up`() {
        assertFalse(layout(276, 338).newBestChip)
        assertFalse(layout(339, 338).newBestChip)
        assertTrue(layout(340, 338).newBestChip)
    }

    @Test
    fun `the wide list has a 22dp flame, the long streak and a block up to 600dp wide, centred`() {
        val layout = layout(620, 430)

        assertEquals(WidgetStep.ListWide, layout.step)
        assertEquals(22f, layout.look.flame.value, EPS)
        assertEquals(40f, layout.look.ring.value, EPS)
        assertTrue(layout.look.longStreak)
        assertEquals(592f, layout.contentWidth.value, EPS)
        assertEquals(600f, layout(900, 430).contentWidth.value, EPS)
        assertEquals(452f, layout(480, 430).contentWidth.value, EPS)
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
