package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.SizeMode
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizesTest {
    @Test
    fun `the widget hands the launcher every layout up front, so a resize redraws at once`() {
        val widget = PuzzleBoxWidget(WidgetStateReader({ flow { } }, { flow { } }, testScoreboard(), BuildWidgetState()))

        val mode = widget.sizeMode

        assertTrue(mode is SizeMode.Responsive)
        assertEquals(WidgetSizes.ALL, (mode as SizeMode.Responsive).sizes)
    }

    @Test
    fun `there are at most 16 sizes, the platform's limit`() {
        assertTrue(WidgetSizes.ALL.size <= 16)
    }

    @Test
    fun `every step is reachable from some size`() {
        assertEquals(WidgetStep.entries.toSet(), WidgetSizes.ALL.map { WidgetStep.of(it) }.toSet())
    }

    @Test
    fun `the sizes go past the old 540 by 360dp cap, to a 6 by 5 placement, since the provider no longer caps the widget`() {
        assertTrue(WidgetSizes.ALL.any { it.width.value >= 700f && it.height.value >= 500f })
    }

    @Test
    fun `the narrowest size is 200dp, so a 3 cell widget gets its own chips1 and chips2`() {
        assertEquals(200f, WidgetSizes.ALL.minOf { it.width.value }, 0f)
        assertTrue(WidgetSizes.ALL.any { it.width.value == 200f && WidgetStep.of(it) == WidgetStep.Chips1 })
        assertTrue(WidgetSizes.ALL.any { it.width.value == 200f && WidgetStep.of(it) == WidgetStep.Chips2 })
    }

    @Test
    fun `the one-row heights are 56 and 90dp, chips2 is 160 and 200dp tall, and the 4x3 lists are 340 and 480 wide at 280 and 320`() {
        fun heights(step: WidgetStep) = WidgetSizes.ALL.filter { WidgetStep.of(it) == step }.map { it.height.value }.toSet()

        assertEquals(setOf(56f, 90f), heights(WidgetStep.Chips1))
        assertEquals(setOf(160f, 200f), heights(WidgetStep.Chips2))
        for (width in listOf(340, 480)) for (height in listOf(280, 320)) {
            assertTrue("$width x $height", DpSize(width.dp, height.dp) in WidgetSizes.ALL)
        }
    }

    @Test
    fun `the lists have a bucket per row height, so a taller widget steps its rows up one size, then another`() {
        val rows = WidgetSizes.ALL.filter { WidgetStep.of(it) != WidgetStep.Chips1 && WidgetStep.of(it) != WidgetStep.Chips2 }
            .map { WidgetLayout.of(it).rowHeight.value }
            .toSet()

        assertEquals(3, rows.size)
    }

    @Test
    fun `a 3 cell wide list has a size that grows its rows, so a 3 by 4 widget shows its pill instead of a short block`() {
        val narrow = WidgetSizes.ALL.filter { it.width.value in 340f..419f && WidgetStep.of(it) == WidgetStep.ListNarrow }

        assertTrue(narrow.any { WidgetLayout.of(it).fixedRows })
        assertTrue(narrow.any { !WidgetLayout.of(it).fixedRows })
    }

    @Test
    fun `a 3 by 2 tablet widget, 340 by 200dp, has its own chips2 size with a ring over 36dp, not the phone's`() {
        val size = DpSize(340.dp, 200.dp)

        assertTrue(size in WidgetSizes.ALL)
        assertTrue(WidgetLayout.of(size).look.ring.value > 36f)
    }

    @Test
    fun `chips2 has a size for each of its rings, the 36dp phone look and the big ones`() {
        val rings = WidgetSizes.ALL
            .filter { WidgetStep.of(it) == WidgetStep.Chips2 }
            .map { WidgetLayout.of(it).look.ring.value }
            .toSet()

        assertTrue(36f in rings)
        assertTrue(rings.any { it >= 48f })
    }

    @Test
    fun `the shortest size is at most 56dp, the least one-row height, and its chips1 block fits with the chips in it`() {
        val shortest = WidgetSizes.ALL.minBy { it.height }
        val layout = WidgetLayout.of(shortest)

        assertTrue(shortest.height.value <= 56f)
        assertEquals(WidgetStep.Chips1, layout.step)
        assertTrue(WidgetLayout.chipsRowHeight(layout.look) <= layout.contentHeight)
        assertTrue(layout.contentHeight <= shortest.height)
    }

    @Test
    fun `chips1 has a size for each of its rings, 36dp at 56 tall and bigger where the height or the width allows`() {
        val rings = WidgetSizes.ALL.filter { WidgetStep.of(it) == WidgetStep.Chips1 }.map { WidgetLayout.of(it).look.ring.value }.toSet()

        assertEquals(setOf(36f, 46f, 56f), rings)
    }
}
