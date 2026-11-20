package com.ivandsky.puzzlebox.insights.recap

import org.junit.Assert.assertEquals
import org.junit.Test

/** How tall each game's band is: its share of the sheet, but never too short to read its name and count. */
class SolvedBandsTest {

    @Test
    fun `bands are as tall as their share of the solves and fill the sheet`() {
        val heights = bandHeights(listOf(50, 30, 20), totalHeight = 500f, min = 48f)

        assertEquals(listOf(250f, 150f, 100f), heights)
    }

    @Test
    fun `a small share is lifted to the minimum and the larger bands give up the difference`() {
        val heights = bandHeights(listOf(28, 24, 22, 14, 8), totalHeight = 560f, min = 56f)

        assertEquals(56f, heights.last(), 0.001f)
        assertEquals(560f, heights.sum(), 0.001f)
        assertEquals(heights[0] / heights[1], 28f / 24f, 0.001f)
    }

    @Test
    fun `a lifted band can lift the next one down in turn`() {
        val heights = bandHeights(listOf(100, 5, 3), totalHeight = 300f, min = 56f)

        assertEquals(listOf(188f, 56f, 56f), heights)
    }

    @Test
    fun `a sheet too short for every band at the minimum shares its height evenly`() {
        assertEquals(listOf(40f, 40f, 40f), bandHeights(listOf(90, 5, 5), totalHeight = 120f, min = 56f))
    }

    @Test
    fun `one band takes the whole sheet`() {
        assertEquals(listOf(700f), bandHeights(listOf(1), totalHeight = 700f, min = 56f))
    }
}
