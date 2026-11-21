package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The chip steps; the list steps are WidgetListLayoutTest.
class WidgetLayoutTest {
    private fun layout(width: Int, height: Int) = WidgetLayout.of(DpSize(width.dp, height.dp))

    @Test
    fun `chips1 has no padding, so a chip is the whole of its slice, and no pill, time line or long streak`() {
        val layout = layout(276, 102)

        assertEquals(WidgetStep.Chips1, layout.step)
        assertEquals(0f, layout.look.horizontalPadding.value, EPS)
        assertEquals(0f, layout.look.verticalPadding.value, EPS)
        assertEquals(102f, layout.contentHeight.value, EPS)
        assertFalse(layout.look.longStreak)
        assertFalse(layout.look.timeLine)
        assertFalse(layout.look.pill)
    }

    @Test
    fun `the chips1 ring is 36dp at 56dp tall with 14sp text`() {
        val look = layout(340, 56).look

        assertEquals(36f, look.ring.value, EPS)
        assertEquals(14f, look.chipFirstText.value, EPS)
    }

    @Test
    fun `the chips1 ring is larger at 110dp tall than at 56, up to 56dp, with 16sp text`() {
        val short = layout(340, 56).look
        val tall = layout(340, 110).look

        assertTrue(tall.ring > short.ring)
        assertEquals(56f, tall.ring.value, EPS)
        assertEquals(16f, tall.chipFirstText.value, EPS)
        assertEquals(56f, layout(900, 159).look.ring.value, EPS)
    }

    @Test
    fun `the chips1 ring is held to its chip's fifth of the width, 4dp short of it`() {
        assertEquals(46f, layout(250, 90).look.ring.value, EPS)
        assertEquals(36f, layout(200, 110).look.ring.value, EPS)
        assertEquals(36f, layout(200, 56).look.ring.value, EPS)
    }

    @Test
    fun `every chips1 size from 200 by 56dp holds its ring and one line, with a ring of 36 to 56dp and 16sp text from 44dp`() {
        for (height in 56..159) for (width in 200..700) {
            val layout = layout(width, height)
            val ring = layout.look.ring.value
            val block = WidgetLayout.chipsRowHeight(layout.look)

            assertTrue("$width x $height block $block of ${layout.contentHeight}", block <= layout.contentHeight)
            assertTrue("$width x $height ring $ring", ring in 36f..56f)
            assertTrue("$width x $height ring $ring is wider than its slice", ring <= maxOf(36f, width / 5f - 4f))
            assertEquals("$width x $height ring $ring", ring >= 44f, layout.look.chipFirstText.value == 16f)
        }
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
    fun `chips2 under 250dp wide, 3 cells, has 8dp padding to give its five chips the room, and 250dp has 14dp`() {
        assertEquals(8f, layout(200, 200).look.horizontalPadding.value, EPS)
        assertEquals(8f, layout(249, 160).look.horizontalPadding.value, EPS)
        assertEquals(14f, layout(250, 160).look.horizontalPadding.value, EPS)
    }

    @Test
    fun `chips2 at 200 by 200dp, the narrowest bucket, fits its streak line, 36dp ring, chip text, pill and two gaps`() {
        val layout = layout(200, 200)
        val needed = layout.look.streakHeight + layout.look.ring + WidgetLayout.chipTextHeight(layout.look) +
            WidgetLayout.PILL_HEIGHT + WidgetLayout.CHIP_GAP_MIN * 2

        assertEquals(WidgetStep.Chips2, layout.step)
        assertEquals(36f, layout.look.ring.value, EPS)
        assertTrue("needs $needed of ${layout.contentHeight}", needed <= layout.contentHeight)
        assertTrue(layout.look.pill)
    }

    @Test
    fun `chips2 at its 160dp threshold, 250 and 200 wide, fits its streak line, 36dp ring row and pill with no more than 160dp`() {
        for (width in listOf(200, 250)) {
            val layout = layout(width, 160)
            val block = layout.look.streakHeight + WidgetLayout.chipsRowHeight(layout.look) + WidgetLayout.PILL_HEIGHT

            assertEquals(WidgetStep.Chips2, layout.step)
            assertEquals("$width ring", 36f, layout.look.ring.value, EPS)
            assertTrue("$width block $block of ${layout.contentHeight}", block <= layout.contentHeight)
            assertTrue("$width block $block and its padding is over 160dp", block + layout.look.verticalPadding * 2 <= 160.dp)
        }
    }

    @Test
    fun `chips2 gives up its vertical padding before its ring, from 14dp at 183dp tall to 4dp at 160dp, and always fits its pill`() {
        assertEquals(14f, layout(250, 183).look.verticalPadding.value, EPS)
        assertEquals(4f, layout(250, 160).look.verticalPadding.value, EPS)
        for (height in 160..182) for (width in 200..540) {
            val layout = layout(width, height)
            val block = layout.look.streakHeight + WidgetLayout.chipsRowHeight(layout.look) + WidgetLayout.PILL_HEIGHT

            assertTrue("$width x $height block $block of ${layout.contentHeight}", block <= layout.contentHeight)
            assertTrue("$width x $height padding ${layout.look.verticalPadding}", layout.look.verticalPadding.value in 4f..14f)
        }
    }

    @Test
    fun `chips2 on the tablet's 3 by 2, 340 by 200dp, has a ring over 44dp and the larger text, a little under the 4 by 2's 49dp`() {
        val look = layout(340, 200).look

        assertTrue("ring ${look.ring}", look.ring.value > 44f)
        assertTrue("ring ${look.ring}", look.ring <= layout(420, 200).look.ring)
        assertEquals(16f, look.chipFirstText.value, EPS)
    }

    @Test
    fun `chips2 says days in a row only from 320dp of content, so the freezes and the done count are never squeezed out`() {
        assertFalse(layout(276, 220).look.longStreak)
        assertFalse(layout(347, 220).look.longStreak)
        assertTrue(layout(348, 220).look.longStreak)
        assertTrue(layout(900, 270).look.longStreak)
        assertFalse(layout(900, 102).look.longStreak)
    }

    @Test
    fun `the block is the widget less its padding, with no cap on either side`() {
        assertEquals(102f, layout(276, 102).contentHeight.value, EPS)
        assertEquals(192f, layout(276, 220).contentHeight.value, EPS)
        assertEquals(251f, layout(900, 279).contentHeight.value, EPS)
    }

    @Test
    fun `a narrow tall widget is chips2 with its block filling the whole height, not stopping at 252dp`() {
        val layout = layout(250, 400)

        assertEquals(WidgetStep.Chips2, layout.step)
        assertEquals(372f, layout.contentHeight.value, EPS)
        assertEquals(572f, layout(200, 600).contentHeight.value, EPS)
    }

    @Test
    fun `chips2 on the tablet's 4 by 2, 470 by 200dp, is as the user confirmed it, a 49dp ring in a 172dp block`() {
        val layout = layout(470, 200)

        assertEquals(49f, layout.look.ring.value, EPS)
        assertEquals(14f, layout.look.verticalPadding.value, EPS)
        assertEquals(172f, layout.contentHeight.value, EPS)
    }

    @Test
    fun `chips2 has the done count from 250dp wide, and the other steps always have it`() {
        assertFalse(layout(249, 200).look.doneCount)
        assertFalse(layout(200, 160).look.doneCount)
        assertTrue(layout(250, 160).look.doneCount)
        assertTrue(layout(420, 200).look.doneCount)
        assertTrue(layout(420, 280).look.doneCount)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
