package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A settling link follows its letters: the trace line until they start, thin at the dip, thick at the peak, whole at rest. */
class StrandsLinkSettleTest {

    private val link = StrandsLink((0 until 4).map { Coord(0, it) }, LinkKind.Theme)
    private val stagger = StrandsMotion.SettleStaggerMs
    private val quarter = StrandsMotion.FoundSettle.enterDurationMillis / 4

    private fun settle(elapsedMs: Int) = LinkSettle(link, FoundThemeSpec, elapsedMs)

    @Test
    fun `at the submit frame every step is the trace line at full width`() {
        val start = settle(0)
        assertEquals(listOf(1f, 1f, 1f), start.stepScales().toList())
        assertEquals(listOf(0f, 0f, 0f), start.stepLit().toList())
    }

    @Test
    fun `a step still holds the trace line until both its letters have started`() {
        // The third letter waits 2 staggers: the step into it is still the trace line, the first is already turning.
        val early = settle(2 * stagger - 1)
        assertEquals(1f, early.stepScales()[2], 0f)
        assertEquals(0f, early.stepLit()[1], 0f)
        assertEquals(0f, early.stepLit()[2], 0f)
        assertTrue(early.stepLit()[0] > 0f)
    }

    @Test
    fun `a step is as thin as its thinner letter at the dip`() {
        val dip = settle(quarter + stagger)
        assertEquals(StrandsMotion.SettleDipScale, dip.stepScales()[0], 0.02f)
        assertTrue(dip.stepScales().all { it < 1f })
    }

    @Test
    fun `a step is thicker than at rest at the peak overshoot`() {
        assertTrue(settle(2 * quarter + stagger / 2).stepScales()[0] > 1.05f)
    }

    @Test
    fun `every step is whole and the link's own once the last letter has settled`() {
        val rest = settle(3 * stagger + StrandsMotion.FoundSettle.enterDurationMillis)
        assertEquals(listOf(1f, 1f, 1f), rest.stepScales().toList())
        assertEquals(listOf(1f, 1f, 1f), rest.stepLit().toList())
    }
}
