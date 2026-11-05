package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** A15: every accepted puzzle has exactly one solution — brute-force solver as the oracle. */
class TangoUniquenessTest {

    /** Counts solutions of [puzzle] by exhaustive search over the empty cells; each found grid goes to [found]. */
    private fun countSolutions(
        puzzle: TangoPuzzle,
        limit: Int = 2,
        found: (List<TangoCell?>) -> Unit = {},
    ): Int {
        val rules = com.ivandsky.puzzlebox.games.tango.engine.TangoRules
        var count = 0
        val empty = (0 until puzzle.cellCount).filter { it !in puzzle.givens }
        val grid = Array<TangoCell?>(puzzle.cellCount) { idx -> puzzle.givens[idx] }

        fun consistentAt(idx: Int): Boolean {
            val candidate = grid.toList()
            val state = com.ivandsky.puzzlebox.games.tango.engine.TangoState(puzzle, candidate)
            return rules.validate(TangoMove(idx, grid[idx]), state) ==
                com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict.Valid
        }

        fun rec(i: Int) {
            if (count >= limit) return
            if (i == empty.size) {
                if (rules.isComplete(com.ivandsky.puzzlebox.games.tango.engine.TangoState(puzzle, grid.toList()))) {
                    count++
                    found(grid.toList())
                }
                return
            }
            val idx = empty[i]
            for (value in listOf(TangoCell.SUN, TangoCell.MOON)) {
                grid[idx] = value
                if (consistentAt(idx)) rec(i + 1)
            }
            grid[idx] = null
        }
        rec(0)
        return count
    }

    @Test
    fun `the bundled sample has exactly one solution`() {
        val puzzle = (TangoCodec.decode(TangoTestSupport.sampleBytes) as DecodeResult.Ok).value
        assertEquals(1, countSolutions(puzzle))
    }

    @Test
    fun `the found solution matches the documented one`() {
        val puzzle = (TangoCodec.decode(TangoTestSupport.sampleBytes) as DecodeResult.Ok).value
        val session = TangoSession(ref = null, puzzle = puzzle, placements = emptyMap())
        // Fill every non-given cell from the documented solution; completion must hold.
        var s = session
        TangoTestSupport.solution.forEachIndexed { idx, cell ->
            if (idx !in puzzle.givens) s = s.withMove(TangoMove(idx, cell), Instant.now())
        }
        assertNotNull(s.completedAt)
    }

    @Test
    fun `the sample is uniquely solvable per the A15 check`() {
        assertTrue(com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness.isUniquelySolvable(TangoTestSupport.sample))
    }

    @Test
    fun `an odd board is rejected as unsolvable by the count rule`() {
        val odd = TangoPuzzle(TangoTestSupport.variant, n = 3, givens = emptyMap(), markers = emptyList())
        assertFalse(com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness.isUniquelySolvable(odd))
    }

    @Test
    fun `an ambiguous puzzle is rejected, never guessed through`() {
        // A 4x4 with a single given has multiple solutions — countSolutions finds 2 quickly.
        val ambiguous = TangoPuzzle(TangoTestSupport.variant, n = 4, givens = mapOf(0 to TangoCell.SUN), markers = emptyList())
        assertFalse(com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness.isUniquelySolvable(ambiguous))
    }

    @Test
    fun `solve returns the brute-force unique solution`() {
        val puzzle = TangoTestSupport.sample
        val oracle = mutableListOf<List<TangoCell?>>()
        assertEquals(1, countSolutions(puzzle, found = { oracle += it }))
        assertEquals(oracle.single(), com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness.solve(puzzle))
        assertEquals(TangoTestSupport.solution, com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness.solve(puzzle))
    }

    @Test
    fun `solve refuses an ambiguous or odd puzzle`() {
        val ambiguous = TangoPuzzle(TangoTestSupport.variant, n = 4, givens = mapOf(0 to TangoCell.SUN), markers = emptyList())
        val odd = TangoPuzzle(TangoTestSupport.variant, n = 3, givens = emptyMap(), markers = emptyList())
        assertNull(com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness.solve(ambiguous))
        assertNull(com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness.solve(odd))
    }
}
