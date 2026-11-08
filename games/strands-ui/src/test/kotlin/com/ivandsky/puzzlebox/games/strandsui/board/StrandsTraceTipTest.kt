package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.gameui.TipKind
import com.ivandsky.puzzlebox.games.strands.Coord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strands' live tip (spec A4): the finger, projected onto the nearest compass from the tail
 * centre, leans the connector's end only toward a letter the board would join next — on the
 * board, free, not found — or back at the previous one; anywhere else it rests.
 */
class StrandsTraceTipTest {

    private val rows = 4
    private val cols = 4

    private val free: (Coord) -> Boolean = { false }

    /** Finger [dx], [dy] cells away from the centre of [tail]. */
    private fun finger(tail: Coord, dx: Float, dy: Float) = centreOf(tail) + Offset(dx, dy)

    private fun tip(path: List<Coord>, tailDx: Float, tailDy: Float, found: (Coord) -> Boolean = free) =
        strandsTip(path, finger(path.last(), tailDx, tailDy), rows, cols, found)

    @Test
    fun `toward a free letter the tip follows the finger, capped short of it`() {
        val tail = Coord(1, 1)
        val near = tip(listOf(tail), 0.3f, 0f)!!
        assertEquals(TipKind.Forward, near.kind)
        assertEquals(Coord(1, 2), near.toward)
        assertEquals(0.3f, near.extent, 1e-4f)

        val far = tip(listOf(tail), 3f, 0f)!!
        assertEquals(MotionTokens.Trace.TipMaxCells, far.extent, 1e-5f)
        assertTrue(far.extent < 1f)
    }

    @Test
    fun `a diagonal tip measures its reach along the diagonal step`() {
        val tail = Coord(1, 1)
        val tip = tip(listOf(tail), 0.2f, 0.2f)!!
        assertEquals(Coord(2, 2), tip.toward)
        assertEquals(0.2f, tip.extent, 1e-4f)
    }

    @Test
    fun `the perpendicular part of the finger moves the tip nowhere off its compass`() {
        // Well inside East's cone (±22.5°), so the south drift changes nothing.
        val tip = tip(listOf(Coord(1, 1)), 0.3f, 0.1f)!!
        assertEquals(Coord(1, 2), tip.toward)
        assertEquals(0.3f, tip.extent, 1e-4f)
    }

    @Test
    fun `toward the previous letter the tip backs up`() {
        val path = listOf(Coord(1, 2), Coord(1, 1))
        val tip = tip(path, 0.3f, 0f)!!
        assertEquals(TipKind.Back, tip.kind)
        assertEquals(Coord(1, 2), tip.toward)
        assertEquals(0.3f, tip.extent, 1e-4f)
    }

    @Test
    fun `into a used letter or off the board there is no tip`() {
        // Tail (1,1); east is (1,2), in the path but not the previous letter.
        val used = listOf(Coord(1, 2), Coord(2, 2), Coord(2, 1), Coord(1, 1))
        assertNull(tip(used, 0.8f, 0f))

        val corner = Coord(0, 0)
        assertNull(tip(listOf(corner), 0f, -0.8f))
    }

    @Test
    fun `toward a found letter there is no tip`() {
        val tail = Coord(1, 1)
        assertNull(tip(listOf(tail), 0.8f, 0f, found = { it == Coord(1, 2) }))
    }

    @Test
    fun `a finger resting on the tail centre draws no tip`() {
        val tail = Coord(1, 1)
        assertNull(tip(listOf(tail), MotionTokens.Trace.TipDeadCells / 2, 0f))
        assertNull(strandsTip(emptyList(), finger(tail, 0.5f, 0f), rows, cols, free))
    }
}
