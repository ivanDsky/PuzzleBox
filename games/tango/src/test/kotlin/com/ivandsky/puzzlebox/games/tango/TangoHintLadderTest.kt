package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoLineRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.feed.TangoGameExtractor
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The forced-move hint ladder (spec hints bullet): marker → triple → count → line fills. */
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

    // ---- line completion rung: real dailies the first three rungs cannot finish ----

    private fun blank(givens: Map<Int, TangoCell>, markers: List<TangoMarker> = emptyList()) =
        TangoPuzzle(TangoTestSupport.variant, n = 6, givens = givens, markers = markers)

    private fun lineFills(row: Int?, col: Int?, fills: Int) = TangoHint.Why.LineFills(row, col, fills)

    @Test
    fun `a column needing counts and triples together hints its bottom cell`() {
        // Daily 758 column 1 reads M M S _ _ _: both fills left end in a sun, no single rung sees it.
        val puzzle = blank(mapOf(1 to TangoCell.MOON, 7 to TangoCell.MOON, 13 to TangoCell.SUN))

        val hint = TangoRules.hint(TangoState(puzzle, TangoTestSupport.gridOf(puzzle)))

        assertEquals(TangoHint.Forced(31, TangoCell.SUN, lineFills(row = null, col = 1, fills = 2)), hint)
    }

    @Test
    fun `chained opposite markers in a row hint the equal pair`() {
        // Daily 759 row 0: S S M _ x _ x _ — cells 3 and 5 equal, so both moons and cell 4 a sun.
        val puzzle = blank(
            mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN, 2 to TangoCell.MOON),
            listOf(
                TangoMarker(3, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(4, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
            ),
        )

        val hint = TangoRules.hint(TangoState(puzzle, TangoTestSupport.gridOf(puzzle)))

        assertEquals(TangoHint.Forced(3, TangoCell.MOON, lineFills(row = 0, col = null, fills = 1)), hint)
    }

    @Test
    fun `the sample's stuck row hints its marker pair`() {
        val puzzle = TangoTestSupport.sample
        var grid = TangoTestSupport.gridOf(puzzle)
        var hint = TangoRules.hint(TangoState(puzzle, grid))
        while (hint is TangoHint.Forced && hint.why !is TangoHint.Why.LineFills) {
            grid = grid.toMutableList().also { it[hint.idx] = hint.value }
            hint = TangoRules.hint(TangoState(puzzle, grid))
        }

        assertEquals(TangoHint.Forced(18, TangoCell.SUN, lineFills(row = 3, col = null, fills = 1)), hint)
    }

    @Test
    fun `a line with no legal fill hints nothing`() {
        // Row 0 holds three suns and a gap-free triple: no fill exists, the rule stays silent.
        val puzzle = blank(mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN, 2 to TangoCell.SUN))

        assertNull(TangoLineRule.deduction(puzzle, TangoTestSupport.gridOf(puzzle)))
    }

    @Test
    fun `hints walk each stuck daily and the sample to the solution without a wrong hint`() {
        listOf(
            dailyOf("linkedin-tango-byid-758.json"),
            dailyOf("01-tango-stream-today.txt"),
            TangoTestSupport.sample,
        ).forEach { puzzle -> assertWalkSolves(puzzle) }
    }

    @Test
    fun `the dailies that never got stuck keep their hints and still solve`() {
        listOf("linkedin-tango-byid-1.json", "linkedin-tango-byid-301.json").forEach { name ->
            val puzzle = dailyOf(name)
            val rungs = assertWalkSolves(puzzle)
            assertTrue(name, rungs.none { it is TangoHint.Why.LineFills })
        }
    }

    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { name }.readText()

    private fun dailyOf(name: String): TangoPuzzle {
        val match = requireNotNull(TangoGameExtractor.extract(fixture(name))) { name }
        val parsed = LotkaWireParser.parse(match.lotkaGamePuzzleJson, LocalDate.of(2026, 11, 5), match.editionText)
        return (parsed as DecodeResult.Ok).value.puzzle
    }

    /** Applies the ladder from the givens to the end; returns the `why` of every hint. */
    private fun assertWalkSolves(puzzle: TangoPuzzle): List<TangoHint.Why> {
        val solution = TangoUniqueness.solve(puzzle)!!
        var grid = TangoTestSupport.gridOf(puzzle)
        val whys = mutableListOf<TangoHint.Why>()
        while (grid.any { it == null }) {
            val hint = TangoRules.hint(TangoState(puzzle, grid)) as? TangoHint.Forced
                ?: error("ladder stuck with ${grid.count { it == null }} cells left")
            assertEquals("hint at ${hint.idx} contradicts the solution", solution[hint.idx], hint.value)
            whys += hint.why
            grid = grid.toMutableList().also { it[hint.idx] = hint.value }
        }
        assertTrue(TangoRules.isComplete(TangoState(puzzle, grid)))
        assertEquals(solution, grid)
        return whys
    }
}
