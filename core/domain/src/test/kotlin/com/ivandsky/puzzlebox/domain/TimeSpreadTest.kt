package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Results histogram and caption maths: adaptive nice-step bins, count-based heights, rank order. */
class TimeSpreadTest {

    private fun score(seconds: Int, hints: Int = 0) = BestScore(seconds * 1_000L, hints)

    private fun spread(
        earlier: List<BestScore>,
        current: BestScore,
        order: RankOrder = RankOrder.TimeThenHints,
    ) = timeSpread(earlier, current, order)

    /** [n] solves a minute apart, 1:00 up to n:00; the last one is this solve. */
    private fun ladder(n: Int) = spread((1 until n).map { score(60 * it) }, score(60 * n))

    @Test
    fun `first solve is one bin holding it at a third of the plot`() {
        val result = spread(emptyList(), score(60))
        assertTrue(result.isFirstSolve)
        assertEquals(1, result.solves)
        assertNull(result.averageEarlierMs)
        assertEquals(1, result.bins.size)
        assertEquals(0, result.youBin)
        assertEquals(1, result.bins[0].count)
        assertEquals(1f / 3f, result.bins[0].height, 0f)
        assertEquals(0, result.beaten)
        assertTrue(result.best)
        assertEquals(1, result.rank)
    }

    @Test
    fun `a single solve is not squeezed into a sliver range`() {
        val bin = spread(emptyList(), score(95)).bins.single()
        // At least a third of the time wide, on a round edge: 95 s / 3 = 31.7 s, so 60 s steps.
        assertEquals(60_000L, bin.fromMs)
        assertEquals(120_000L, bin.toMs)
    }

    @Test
    fun `bin count follows the solve count at every boundary`() {
        val sizes = listOf(1, 2, 3, 4, 5, 8, 9, 16, 17, 40, 41).map { ladder(it).bins.size }
        assertEquals(listOf(1, 2, 3, 4, 5, 5, 5, 6, 6, 5, 9), sizes)
    }

    @Test
    fun `bins never exceed the target and hold every solve`() {
        val targets = mapOf(1 to 1, 2 to 2, 4 to 4, 5 to 5, 8 to 5, 9 to 6, 16 to 6, 17 to 8, 40 to 8, 41 to 10)
        targets.forEach { (n, target) ->
            val result = ladder(n)
            assertTrue("n=$n", result.bins.size <= target)
            assertEquals("n=$n", n, result.bins.sumOf { it.count })
            assertEquals("n=$n", n, result.solves)
        }
    }

    @Test
    fun `bin edges are round and contiguous`() {
        val bins = ladder(17).bins
        bins.forEach { assertEquals(0L, it.fromMs % 5_000L) }
        bins.zipWithNext { a, b -> assertEquals(a.toMs, b.fromMs) }
        assertEquals(0L, bins.first().fromMs)
        assertEquals(1_080_000L, bins.last().toMs)
    }

    @Test
    fun `step is the smallest nice one that fits`() {
        // 60 s and 100 s in two bins: 20 s steps give three, 30 s steps give two.
        val result = spread(listOf(score(60)), score(100))
        assertEquals(listOf(60_000L, 90_000L), result.bins.map { it.fromMs })
        assertEquals(listOf(90_000L, 120_000L), result.bins.map { it.toMs })
    }

    @Test
    fun `this solve's bin and a new best`() {
        val result = spread(listOf(score(100), score(80), score(100)), score(50))
        assertEquals(4, result.solves)
        assertEquals(93_333L, result.averageEarlierMs)
        assertEquals(0, result.youBin)
        assertEquals(1, result.bins[0].count)
        assertEquals(3, result.beaten)
        assertTrue(result.best)
        assertFalse(result.isFirstSolve)
        assertEquals(1, result.rank)
    }

    @Test
    fun `one or two solves do not shoot to full height`() {
        val two = spread(listOf(score(60)), score(100))
        assertEquals(listOf(1f / 3f, 1f / 3f), two.bins.map { it.height })
    }

    @Test
    fun `heights are count over the tallest, at least three, with a floor`() {
        // Twenty-one 100s and one 50: the single solve's share 1/21 is under 6 percent.
        val earlier = List(20) { score(100) } + score(50)
        val result = spread(earlier, score(100))
        val tall = result.bins.maxBy { it.count }
        assertEquals(1f, tall.height, 0f)
        val short = result.bins.first { it.count == 1 }
        assertEquals(0.06f, short.height, 0f)
        result.bins.filter { it.count == 0 }.forEach { assertEquals(0f, it.height, 0f) }
    }

    @Test
    fun `a tallest bar of three fills the plot`() {
        val result = spread(listOf(score(60), score(60)), score(60))
        assertEquals(1f, result.bins.single().height, 0f)
    }

    @Test
    fun `a middle solve beats the slower ones and ranks behind the faster ones`() {
        val result = spread(listOf(score(40), score(60), score(80), score(100)), score(70))
        assertEquals(2, result.beaten)
        assertFalse(result.best)
        assertEquals(5, result.solves)
        assertEquals(3, result.rank)
    }

    @Test
    fun `the slowest solve beats nobody and ranks last`() {
        val result = spread(listOf(score(40), score(60)), score(90))
        assertEquals(0, result.beaten)
        assertFalse(result.best)
        assertEquals(result.bins.lastIndex, result.youBin)
        assertEquals(3, result.rank)
    }

    @Test
    fun `ties are not beaten, not ahead and not a best`() {
        val result = spread(listOf(score(60), score(60), score(90)), score(60))
        assertEquals(1, result.beaten)
        assertFalse(result.best)
        assertEquals(1, result.rank)
    }

    @Test
    fun `identical times share one bin that is not a sliver`() {
        val result = spread(listOf(score(60), score(60)), score(60))
        assertEquals(1, result.bins.size)
        assertEquals(0, result.youBin)
        assertEquals(3, result.bins[0].count)
        assertEquals(60_000L, result.bins[0].fromMs)
        assertEquals(80_000L, result.bins[0].toMs)
        assertEquals(0, result.beaten)
    }

    @Test
    fun `hints first ranks fewer hints ahead of time but still bins by time`() {
        val earlier = listOf(score(30, hints = 2), score(40, hints = 0), score(90, hints = 1))
        val result = spread(earlier, score(80, hints = 0), RankOrder.HintsThenTime)
        // Hint-free but slower than the 40s hint-free: beats the two with hints, not the 40s.
        assertEquals(2, result.beaten)
        assertFalse(result.best)
        assertEquals(2, result.rank)
        // 30, 40, 80, 90 in 20 s steps; the empty 60-80 slot stays on the axis.
        assertEquals(listOf(1, 1, 0, 2), result.bins.map { it.count })
        assertEquals(20_000L, result.bins.first().fromMs)
        assertEquals(3, result.youBin)
    }

    @Test
    fun `hints first new best needs fewer hints`() {
        val earlier = listOf(score(30, hints = 1), score(40, hints = 1))
        val result = spread(earlier, score(200, hints = 0), RankOrder.HintsThenTime)
        assertTrue(result.best)
        assertEquals(2, result.beaten)
        assertEquals(1, result.rank)
    }

    @Test
    fun `one hour times bin on whole minutes`() {
        val result = spread(listOf(score(3_600), score(3_723)), score(3_700))
        assertEquals(3, result.bins.size)
        assertEquals(1, result.beaten)
        assertEquals(2, result.rank)
        assertEquals(3_600_000L, result.bins.first().fromMs)
        assertEquals(3_780_000L, result.bins.last().toMs)
    }
}
