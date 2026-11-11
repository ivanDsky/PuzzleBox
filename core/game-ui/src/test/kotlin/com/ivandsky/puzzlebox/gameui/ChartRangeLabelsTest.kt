package com.ivandsky.puzzlebox.gameui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The edge labels of a chart with many bins: centred on their edge, never clipped, never touching. */
class ChartRangeLabelsTest {

    private val gap = 24f

    private fun check(widths: List<Float>, total: Float) {
        val slot = total / (widths.size - 1)
        val placed = placeEdgeLabels(widths, slot, total, gap)
        assertTrue("some labels drawn", placed.isNotEmpty())
        placed.forEach { label ->
            val w = widths[label.index]
            assertEquals("centred on its edge", slot * label.index - w / 2f, label.x, 0.001f)
            assertTrue("inside the left bound", label.x >= 0f)
            assertTrue("inside the right bound", label.x + w <= total)
        }
        placed.zipWithNext().forEach { (a, b) ->
            assertTrue("no overlap", b.x >= a.x + widths[a.index] + gap)
        }
    }

    @Test
    fun `nine bins on a tablet draw no clipped or touching label`() = check(List(10) { 90f }, total = 1500f)

    @Test
    fun `nine bins on a phone draw no clipped or touching label`() = check(List(10) { 90f }, total = 900f)

    @Test
    fun `an hour plus time is wider and thins further`() = check(List(10) { 150f }, total = 900f)

    @Test
    fun `the first and last edge are skipped, not pulled inward`() {
        val widths = List(10) { 90f }
        val placed = placeEdgeLabels(widths, 1500f / 9, 1500f, gap)

        assertFalse(placed.any { it.index == 0 })
        assertFalse(placed.any { it.index == 9 })
    }

    @Test
    fun `labels thin to every k-th edge when the slots are narrow`() {
        val widths = List(10) { 90f }
        val placed = placeEdgeLabels(widths, 900f / 9, 900f, gap)

        assertEquals(listOf(2, 4, 6, 8), placed.map { it.index })
    }
}
