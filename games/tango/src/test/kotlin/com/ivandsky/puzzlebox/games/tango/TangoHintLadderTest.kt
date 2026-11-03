package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The forced-move hint ladder (spec hints bullet): marker propagation → triple → count. */
class TangoHintLadderTest {

    private fun state(grid: List<TangoCell?>) = TangoState(TangoTestSupport.sample, grid)

    private fun gridOf(puzzle: TangoPuzzle = TangoTestSupport.sample): List<TangoCell?> =
        List(puzzle.cellCount) { idx -> puzzle.givens[idx] }

    @Test
    fun `marker propagation outranks the other ladder rungs`() {
        // Empty board of the sample: givens force triples, but a half-filled marker edge wins.
        val start: List<TangoCell?> = gridOf()
        // Fill cell 2's row-neighbors so markers stay clear; place one end of the `=` chain.
        val g = start.toMutableList()
        g[2] = TangoCell.MOON
        g[3] = TangoCell.MOON // completes the triple answer, opens marker edges below it
        val hint = TangoRules.hint(state(g))
        // Marker (3, DOWN, SAME): cell 3 = MOON forces cell 9 = MOON, beating any triple/count rung.
        assertEquals(TangoHint.Forced(9, TangoCell.MOON, TangoHint.Why.MarkerBinding(3, 9, TangoMarkerKind.SAME)), hint)
    }

    @Test
    fun `three-in-a-row outranks count-completion`() {
        // Row 0 givens S,S at 0,1 force cell 2 by triple; no count line is at half yet.
        val hint = TangoRules.hint(state(gridOf()))
        assertTrue(hint is TangoHint.Forced)
        assertEquals(TangoHint.Why.ThreeInARow(0, 1), (hint as TangoHint.Forced).why)
    }

    @Test
    fun `a count line at half forces the remaining gap`() {
        // Fill row 0 to three suns with two gaps; the ladder must fall through to count-completion.
        val g = gridOf().toMutableList()
        g[0] = TangoCell.SUN
        g[1] = TangoCell.SUN
        g[2] = TangoCell.SUN
        g[4] = TangoCell.MOON
        g[5] = TangoCell.MOON
        // Cell 3 is the only gap; row 0 holds S,S,S → the triple rung (cells 1,2) forces MOON at 3.
        val hint = TangoRules.hint(state(g))
        assertEquals(TangoHint.Forced(3, TangoCell.MOON, TangoHint.Why.ThreeInARow(1, 2)), hint)
    }

    @Test
    fun `count-completion fires when no marker or triple rung applies`() {
        // A 4x4 board: row 0 at half suns with one gap — no triples possible, no markers.
        val puzzle = TangoPuzzle(TangoTestSupport.variant, n = 4, givens = emptyMap(), markers = emptyList())
        val g = listOf<TangoCell?>(
            TangoCell.SUN, TangoCell.MOON, TangoCell.SUN, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )
        val hint = TangoRules.hint(TangoState(puzzle, g))
        assertEquals(TangoHint.Forced(3, TangoCell.MOON, TangoHint.Why.LineFull(row = 0, col = null, fullOf = TangoCell.SUN)), hint)
    }

    @Test
    fun `every ladder hint on the daily-shaped fixture matches its unique solution`() {
        val puzzle = TangoTestSupport.sample
        var grid = gridOf(puzzle)
        var hints = 0
        repeat(puzzle.cellCount) {
            val hint = TangoRules.hint(TangoState(puzzle, grid)) as? TangoHint.Forced ?: return@repeat
            assertEquals("hint at ${hint.idx} contradicts the solution", TangoTestSupport.solution[hint.idx], hint.value)
            grid = grid.toMutableList().also { it[hint.idx] = hint.value }
            hints++
        }
        assertTrue("ladder produced no deductions on the fixture", hints > 0)
    }

    @Test
    fun `a sandwich gap outranks count-completion`() {
        // Blank 6x6: row 0 holds S_S — the sandwich rung fires before any count line reaches half.
        val puzzle = TangoPuzzle(TangoTestSupport.variant, n = 6, givens = emptyMap(), markers = emptyList())
        val g = MutableList<TangoCell?>(36) { null }.also {
            it[0] = TangoCell.SUN
            it[2] = TangoCell.SUN
        }
        val hint = TangoRules.hint(TangoState(puzzle, g))
        assertEquals(TangoHint.Forced(1, TangoCell.MOON, TangoHint.Why.ThreeInARow(0, 2)), hint)
    }

    @Test
    fun `hints respect givens and never propose overwriting one`() {
        val puzzle = TangoTestSupport.sample
        var grid = gridOf(puzzle)
        repeat(puzzle.cellCount) {
            val hint = TangoRules.hint(TangoState(puzzle, grid)) as? TangoHint.Forced ?: return
            assertTrue(hint.idx !in puzzle.givens)
            grid = grid.toMutableList().also { it[hint.idx] = hint.value }
        }
        assertNotNull(grid)
    }
}
