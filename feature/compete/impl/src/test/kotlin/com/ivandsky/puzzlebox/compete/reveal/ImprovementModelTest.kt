package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.resultui.DuelSideState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A self duel's gap to the first solve, in whole seconds, and the summary line over all the gaps. */
class ImprovementModelTest {

    private fun time(ms: Long) = DuelSideState.Time(ms, 0)

    @Test
    fun aShorterTimeThanTheFirstSolveIsQuicker() {
        val gap = improvementOf(time(58_000), time(72_000))

        assertEquals(Improvement(Trend.Quicker, 14), gap)
        assertEquals("14 s quicker than your first solve", gap?.text)
    }

    @Test
    fun aLongerTimeIsSlower() {
        val gap = improvementOf(time(65_000), time(58_000))

        assertEquals("7 s slower than your first solve", gap?.text)
        assertEquals(-7L, gap?.signedSeconds)
    }

    @Test
    fun theSameWholeSecondIsTheSame() {
        val gap = improvementOf(time(104_100), time(104_900))

        assertEquals(Improvement(Trend.Same, 0), gap)
        assertEquals("Same as your first solve", gap?.text)
    }

    @Test
    fun timesAreFlooredToWholeSecondsBeforeTheyAreCompared() {
        assertEquals(1L, improvementOf(time(58_999), time(59_000))?.seconds)
    }

    @Test
    fun theHintsDoNotChangeTheGapOrItsText() {
        val gap = improvementOf(DuelSideState.Time(95_000, hints = 1), DuelSideState.Time(80_000, hints = 3))

        assertEquals(Improvement(Trend.Slower, 15), gap)
        assertEquals("15 s slower than your first solve", gap?.text)
    }

    @Test
    fun noTimeOnEitherSideIsNoGap() {
        assertNull(improvementOf(DuelSideState.GaveUp, time(60_000)))
        assertNull(improvementOf(time(60_000), DuelSideState.NotPlayed))
    }

    @Test
    fun theSummaryCountsTheQuickerPuzzlesAndSumsTheGaps() {
        val gaps = listOf(Improvement(Trend.Quicker, 14), Improvement(Trend.Quicker, 42), Improvement(Trend.Slower, 15))

        assertEquals("Faster on 2 of 3 · 0:41 quicker overall", improvementSummary(gaps))
    }

    @Test
    fun aNetLossReadsSlowerOverall() {
        val gaps = listOf(Improvement(Trend.Slower, 7), Improvement(Trend.Quicker, 12), Improvement(Trend.Slower, 21))

        assertEquals("Faster on 1 of 3 · 0:16 slower overall", improvementSummary(gaps))
    }

    @Test
    fun aNetOfNothingReadsSameTimeOverall() {
        val gaps = listOf(Improvement(Trend.Quicker, 5), Improvement(Trend.Slower, 5))

        assertEquals("Faster on 1 of 2 · same time overall", improvementSummary(gaps))
    }

    @Test
    fun puzzlesWithoutAGapAreLeftOutAndNoGapAtAllIsNoLine() {
        assertEquals("Faster on 1 of 1 · 0:03 quicker overall", improvementSummary(listOf(null, Improvement(Trend.Quicker, 3))))
        assertNull(improvementSummary(listOf(null, null)))
        assertNull(improvementSummary(emptyList()))
    }
}
