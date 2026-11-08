package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Results histogram and caption maths: 10 time bins, beating and best by the rank order. */
class TimeSpreadTest {

    private fun score(seconds: Int, hints: Int = 0) = BestScore(seconds * 1_000L, hints)

    private fun spread(
        earlier: List<BestScore>,
        current: BestScore,
        order: RankOrder = RankOrder.TimeThenHints,
    ) = timeSpread(earlier, current, order)

    @Test
    fun `first solve carries no bins`() {
        val result = spread(emptyList(), score(60))
        assertTrue(result.isFirstSolve)
        assertEquals(1, result.solves)
        assertNull(result.averageEarlierMs)
        assertTrue(result.bins.isEmpty())
        assertNull(result.youBin)
        assertEquals(0, result.beaten)
    }

    @Test
    fun `new best lands in the first bin and beats everyone`() {
        val result = spread(listOf(score(100), score(80), score(100)), score(50))
        assertEquals(4, result.solves)
        assertEquals(93_333L, result.averageEarlierMs)
        assertEquals(10, result.bins.size)
        assertEquals(0, result.youBin)
        assertEquals(1, result.bins[0].count)
        assertEquals(2, result.bins[9].count)
        assertEquals(3, result.beaten)
        assertTrue(result.best)
        assertFalse(result.isFirstSolve)
    }

    @Test
    fun `axis spans 3 percent beyond the extremes`() {
        val result = spread(listOf(score(100)), score(50))
        assertEquals(48_500L, result.loMs)
        assertEquals(103_000L, result.hiMs)
    }

    @Test
    fun `heights are count over the tallest with a floor`() {
        // Twenty-one 100s and one 50: the single bin's share 1/21 is under 6 percent.
        val earlier = List(20) { score(100) } + score(50)
        val result = spread(earlier, score(100))
        val tall = result.bins.maxBy { it.count }
        assertEquals(1f, tall.height, 0f)
        val short = result.bins.first { it.count == 1 }
        assertEquals(0.06f, short.height, 0f)
        result.bins.filter { it.count == 0 }.forEach { assertEquals(0f, it.height, 0f) }
    }

    @Test
    fun `a middle solve beats the slower ones`() {
        val result = spread(listOf(score(40), score(60), score(80), score(100)), score(70))
        assertEquals(2, result.beaten)
        assertFalse(result.best)
        assertEquals(5, result.solves)
    }

    @Test
    fun `the slowest solve beats nobody`() {
        val result = spread(listOf(score(40), score(60)), score(90))
        assertEquals(0, result.beaten)
        assertFalse(result.best)
        assertEquals(9, result.youBin)
    }

    @Test
    fun `ties are not beaten and not a best`() {
        val result = spread(listOf(score(60), score(60), score(90)), score(60))
        assertEquals(1, result.beaten)
        assertFalse(result.best)
    }

    @Test
    fun `all equal times share one bin that holds this solve`() {
        val result = spread(listOf(score(60), score(60)), score(60))
        val youBin = result.youBin!!
        assertEquals(3, result.bins[youBin].count)
        assertEquals(1, result.bins.count { it.count > 0 })
        assertEquals(0, result.beaten)
    }

    @Test
    fun `a single earlier solve gives two bins`() {
        val result = spread(listOf(score(100)), score(60))
        assertEquals(2, result.bins.count { it.count > 0 })
        assertEquals(0, result.youBin)
        assertEquals(1, result.beaten)
        assertTrue(result.best)
    }

    @Test
    fun `hints first ranks fewer hints ahead of time but still bins by time`() {
        val earlier = listOf(score(30, hints = 2), score(40, hints = 0), score(90, hints = 1))
        val result = spread(earlier, score(80, hints = 0), RankOrder.HintsThenTime)
        // Hint-free but slower than the 40s hint-free: beats the two with hints, not the 40s.
        assertEquals(2, result.beaten)
        assertFalse(result.best)
        assertEquals(10, result.bins.size)
        assertEquals(30_000L * 0.97, result.loMs.toDouble(), 1.0)
        assertEquals(90_000L * 1.03, result.hiMs.toDouble(), 1.0)
    }

    @Test
    fun `hints first new best needs fewer hints`() {
        val earlier = listOf(score(30, hints = 1), score(40, hints = 1))
        val result = spread(earlier, score(200, hints = 0), RankOrder.HintsThenTime)
        assertTrue(result.best)
        assertEquals(2, result.beaten)
    }

    @Test
    fun `one hour times bin without overflow`() {
        val result = spread(listOf(score(3_600), score(3_723)), score(3_700))
        assertEquals(10, result.bins.size)
        assertEquals(1, result.beaten)
        assertEquals(3_492_000L, result.loMs)
        assertEquals(3_834_690L, result.hiMs)
    }
}
