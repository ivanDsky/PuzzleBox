package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A settling link's thickness follows its letters: absent before they start, thin at the dip, thick at the peak, whole at rest. */
class StrandsLinkSettleTest {

    private val link = StrandsLink((0 until 4).map { Coord(0, it) }, LinkKind.Theme)
    private val stagger = StrandsMotion.SettleStaggerMs
    private val quarter = StrandsMotion.FoundSettle.enterDurationMillis / 4

    private fun widths(elapsedMs: Int) = LinkSettle(link, FoundThemeSpec, elapsedMs).stepWidths().toList()

    @Test
    fun `a step is absent until both its letters have started`() {
        // The third letter waits 2 staggers: the step into it is absent, the first is already coming in.
        val early = widths(2 * stagger - 1)
        assertEquals(0f, early[1], 0f)
        assertEquals(0f, early[2], 0f)
        assertTrue(early[0] > 0f)
    }

    @Test
    fun `a step is as thin as its thinner letter at the dip`() {
        val dip = widths(quarter + stagger)
        assertEquals(StrandsMotion.SettleDipScale, dip[0], 0.02f)
        assertTrue(dip.all { it < 1f })
    }

    @Test
    fun `a step is thicker than at rest at the peak overshoot`() {
        val peak = widths(2 * quarter + stagger / 2)
        assertTrue(peak[0] > 1.05f)
    }

    @Test
    fun `every step is whole once the last letter has settled`() {
        val rest = widths(3 * stagger + StrandsMotion.FoundSettle.enterDurationMillis)
        assertEquals(listOf(1f, 1f, 1f), rest)
    }
}
