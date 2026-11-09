package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoCell.MOON
import com.ivandsky.puzzlebox.games.tango.TangoCell.SUN
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoEqualPairRuleTest {

    private fun board(vararg markers: TangoMarker): TangoPuzzle =
        TangoTestSupport.sample.copy(givens = emptyMap(), markers = markers.toList())

    private fun grid(puzzle: TangoPuzzle, vararg cells: Pair<Int, TangoCell>): List<TangoCell?> =
        MutableList<TangoCell?>(puzzle.cellCount) { null }.apply { cells.forEach { (idx, v) -> this[idx] = v } }

    private fun step(idx: Int, value: TangoCell, vararg evidence: Int) =
        TangoStep.Deduction(TangoStepRung.EQUAL_PAIR, idx, value, evidence.toSet(), row = null, col = null, options = null)

    @Test
    fun `a moon left of an equal pair makes both cells suns`() {
        val puzzle = board(TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))

        val steps = TangoEqualPairRule.forced(puzzle, grid(puzzle, 0 to MOON))

        assertEquals(listOf(step(1, SUN, 0, 1, 2), step(2, SUN, 0, 1, 2)), steps)
    }

    @Test
    fun `a sun right of an equal pair makes both cells moons`() {
        val puzzle = board(TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))

        val steps = TangoEqualPairRule.forced(puzzle, grid(puzzle, 3 to SUN))

        assertEquals(listOf(step(1, MOON, 3, 1, 2), step(2, MOON, 3, 1, 2)), steps)
    }

    @Test
    fun `an equal pair in a column reads the cell below it`() {
        val puzzle = board(TangoMarker(6, TangoEdgeSide.DOWN, TangoMarkerKind.SAME))

        val steps = TangoEqualPairRule.forced(puzzle, grid(puzzle, 18 to MOON))

        assertEquals(listOf(step(6, SUN, 18, 6, 12), step(12, SUN, 18, 6, 12)), steps)
    }

    @Test
    fun `an equal pair with no known neighbour yields nothing`() {
        val puzzle = board(TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))

        assertTrue(TangoEqualPairRule.forced(puzzle, grid(puzzle, 4 to MOON)).isEmpty())
    }

    @Test
    fun `an equal pair with one end filled yields nothing`() {
        val puzzle = board(TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))

        assertTrue(TangoEqualPairRule.forced(puzzle, grid(puzzle, 0 to MOON, 1 to SUN)).isEmpty())
    }

    @Test
    fun `an opposite pair beside a known cell yields nothing`() {
        val puzzle = board(TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        assertTrue(TangoEqualPairRule.forced(puzzle, grid(puzzle, 0 to MOON)).isEmpty())
    }

    @Test
    fun `every forced cell is sound on the blanked fixture sweep`() {
        val solution = TangoTestSupport.solution
        val steps = sweep().flatMap { grid -> TangoEqualPairRule.forced(TangoTestSupport.sample, grid).map { grid to it } }

        assertTrue(steps.isNotEmpty())
        for ((grid, step) in steps) {
            assertEquals(solution[step.idx], step.value)
            assertNull(grid[step.idx])
        }
    }

    @Test
    fun `repeated calls agree and leave the grid untouched`() {
        for (grid in sweep()) {
            val before = grid.toList()
            val first = TangoEqualPairRule.forced(TangoTestSupport.sample, grid)

            assertEquals(first, TangoEqualPairRule.forced(TangoTestSupport.sample, grid))
            assertEquals(before, grid)
        }
    }

    /** The fixture solution with 0..n cells blanked in a fixed pseudo-random order. */
    private fun sweep(): List<List<TangoCell?>> {
        val solution = TangoTestSupport.solution
        val order = solution.indices.shuffled(Random(16))
        return (0..solution.size).map { blanked ->
            solution.toMutableList<TangoCell?>().apply { order.take(blanked).forEach { this[it] = null } }
        }
    }
}
