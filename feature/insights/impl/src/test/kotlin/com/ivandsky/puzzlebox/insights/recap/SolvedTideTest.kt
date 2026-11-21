package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tide's arithmetic: where the bands stand, what the total shows under a level, when a band lands, the wave, the hero's size. */
class SolvedTideTest {

    private val bands = listOf(TideBand(288, bottom = 0.5f, height = 0.5f), TideBand(100, bottom = 0f, height = 0.5f))

    @Test
    fun `bands stack top down as fractions of the sheet, the last on its bottom edge`() {
        assertEquals(listOf(TideBand(3, 0.25f, 0.75f), TideBand(1, 0f, 0.25f)), tideBands(listOf(3, 1), listOf(30f, 10f)))
        assertEquals(1f, tideBands(listOf(3, 1), listOf(30f, 10f)).first().reach, 0f)
        assertEquals(emptyList<TideBand>(), tideBands(listOf(3), listOf(0f)))
    }

    @Test
    fun `the total counts the solves under the level`() {
        assertEquals(0, tideTotal(0f, bands))
        assertEquals(388, tideTotal(1f, bands))
        assertEquals("the bottom band full, half the top one", 100 + 144, tideTotal(0.75f, bands))
        assertEquals("a band with no height adds nothing", 0, tideTotal(1f, listOf(TideBand(5, 0f, 0f))))
    }

    @Test
    fun `a settled tide shows the total, an empty one nothing`() {
        assertEquals(96, SolvedTide(96, 1f).shownTotal())
        assertEquals(0, SolvedTide(388, 0f).also { it.bands = bands }.shownTotal())
    }

    @Test
    fun `a band lands as the level clears it, the bottom first and the top inside the rise`() {
        assertEquals(200, tideLandMs(0f))
        assertTrue("top band lands at ${tideLandMs(1f)}", tideLandMs(1f) in 1877..1900)
        val grid = (0..100).map { tideLandMs(it / 100f) }
        assertTrue("land times never go back: $grid", grid.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `the stripe waits for the tide whatever the game count`() {
        assertEquals(2130, stripeStartMs())
    }

    @Test
    fun `the wave grows over the first sixth and calms to nothing at the top`() {
        assertEquals(0f, tideWave(0f).value, 0f)
        assertEquals(0f, tideWave(1f).value, 0f)
        assertEquals(8.333f, tideWave(1 / 6f).value, 0.01f)
        val peak = (0..600).maxOf { tideWave(it / 600f).value }
        assertEquals(tideWave(1 / 6f).value, peak, 0.01f)
        assertTrue(tideWave(0.5f) < 10.dp)
    }

    @Test
    fun `a wide pane fits the total to its column up to 300sp`() {
        assertEquals(300.sp, heroFontSize(1, striped = false, wide = true, colW = 436f, measured = 57f))
        val huge = heroFontSize(12345, striped = true, wide = true, colW = 664f, measured = 5 * 57f)
        val year = heroFontSize(1068, striped = true, wide = true, colW = 664f, measured = 4 * 57f)
        assertTrue("five digits ($huge) are smaller than four ($year)", huge < year)
        assertEquals("no taller than the room", 100f, heroFontSize(1, striped = false, wide = true, colW = 436f, measured = 57f, roomSp = 80f).value, 0.01f)
    }

    @Test
    fun `a phone keeps the stepped size and shrinks only to fit`() {
        assertEquals(144.sp, heroFontSize(96, striped = true, wide = false, colW = 1000f, measured = 100f))
        assertEquals("the Fastest ceiling: a three-digit total", 132.sp, heroFontSize(100, striped = false, wide = false, colW = 1000f, measured = 100f))
        assertEquals(90f, heroFontSize(12345, striped = true, wide = false, colW = 900f, measured = 1000f).value, 0.01f)
    }
}
