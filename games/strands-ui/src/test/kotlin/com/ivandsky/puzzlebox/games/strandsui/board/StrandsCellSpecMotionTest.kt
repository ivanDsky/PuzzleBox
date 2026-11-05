package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strandsui.FoundEvent
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC10 / A33 on every cell look: reduced motion is the final frame from t0, full motion
 * ends at rest within its pattern, and the hint's first stage moves every letter
 * alike so its motion leaks no order (A11).
 */
class StrandsCellSpecMotionTest {

    private val specs = listOf(IdleSpec, TraceOverlaySpec, FoundThemeSpec, FoundSpangramSpec, HintOutlinedSpec, HintOrderRevealedSpec)
    private val cue = StrandsMotionCue(order = 2, count = 4)

    @Test
    fun `reduced motion is the final frame from t0`() {
        for (spec in specs) {
            for (t in listOf(0, 1, 100, spec.motionMs(cue) / 2)) {
                assertEquals("$spec at $t", StrandsCellFrame.Rest, spec.frame(t, cue, reduceMotion = true))
            }
        }
    }

    @Test
    fun `full motion ends at rest and moves on the way`() {
        for (spec in specs) {
            val total = spec.motionMs(cue)
            assertEquals("$spec at end", StrandsCellFrame.Rest, spec.frame(total, cue, reduceMotion = false))
            if (spec == IdleSpec) continue
            val moving = (0 until total step 10).any { spec.frame(it, cue, reduceMotion = false) != StrandsCellFrame.Rest }
            assertTrue("$spec never moves", moving)
        }
    }

    @Test
    fun `a long spangram's last letter starts within the sweep cap`() {
        // 20 letters at the plain 75 ms stagger would start the last at 1425 ms; the cap squeezes it.
        val last = StrandsMotionCue(order = 19, count = 20)
        val cap = StrandsMotion.SpangramSweep.enterDurationMillis
        assertNotEquals(StrandsCellFrame(lit = 0f), FoundSpangramSpec.frame(cap + 1, last, reduceMotion = false))
    }

    @Test
    fun `a found letter waits beige for its turn, then bounces`() {
        assertEquals(StrandsCellFrame(lit = 0f), FoundThemeSpec.frame(99, cue, reduceMotion = false))
        val dip = FoundThemeSpec.frame(100 + StrandsMotion.FoundSettle.enterDurationMillis / 4, cue, reduceMotion = false)
        assertEquals(StrandsMotion.SettleDipScale, dip.scale, 1e-3f)
        assertEquals(1f, dip.lit)
    }

    @Test
    fun `the outlined hint pulses every letter alike`() {
        for (t in 0..StrandsMotion.HintPulse.enterDurationMillis step 50) {
            val frames = (0 until 6).map { HintOutlinedSpec.frame(t, StrandsMotionCue(it, 6), reduceMotion = false) }
            assertEquals("at $t", 1, frames.distinct().size)
        }
    }

    @Test
    fun `the revealed hint pulses in spelling order`() {
        val first = StrandsMotionCue(order = 0)
        val second = StrandsMotionCue(order = 1)
        val t = StrandsMotion.HintRevealStaggerMs / 2
        assertNotEquals(StrandsCellFrame.Rest, HintOrderRevealedSpec.frame(t, first, reduceMotion = false))
        assertEquals(StrandsCellFrame.Rest, HintOrderRevealedSpec.frame(t, second, reduceMotion = false))
    }

    @Test
    fun `only the word just found moves, placed by its spelling order`() {
        val found = FoundEvent(listOf(Coord(1, 0), Coord(1, 1)), spangram = true, seq = 7)
        val second = StrandsCell(Coord(1, 1), 'A', CellState.FoundSpangram, traceIndex = null)
        val older = StrandsCell(Coord(0, 0), 'P', CellState.FoundTheme, traceIndex = null)
        assertEquals(StrandsMotionTrigger(7, StrandsMotionCue(1, 2)), FoundSpangramSpec.trigger(second, found))
        assertNull(FoundThemeSpec.trigger(older, found))
        assertNull(FoundThemeSpec.trigger(older, null))
        val hinted = StrandsCell(Coord(2, 0), 'T', CellState.HintOrderRevealed(3), traceIndex = null)
        assertEquals(StrandsMotionCue(order = 3), HintOrderRevealedSpec.trigger(hinted, null)?.cue)
    }
}
