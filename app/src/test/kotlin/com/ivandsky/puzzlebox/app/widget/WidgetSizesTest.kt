package com.ivandsky.puzzlebox.app.widget

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
    fun `no size is past the provider's 540 by 360dp cap`() {
        assertTrue(WidgetSizes.ALL.all { it.width.value <= 540f && it.height.value <= 360f })
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
}
