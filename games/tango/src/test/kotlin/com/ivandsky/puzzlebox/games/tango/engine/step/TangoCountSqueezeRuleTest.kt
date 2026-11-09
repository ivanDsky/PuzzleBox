package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoCell.MOON
import com.ivandsky.puzzlebox.games.tango.TangoCell.SUN
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.game.VariantId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoCountSqueezeRuleTest {

    private fun board(n: Int = 6, vararg markers: TangoMarker): TangoPuzzle =
        TangoPuzzle(VariantId("tango/n$n"), n, emptyMap(), markers.toList())

    private fun grid(puzzle: TangoPuzzle, vararg cells: Pair<Int, TangoCell>): List<TangoCell?> =
        MutableList<TangoCell?>(puzzle.cellCount) { null }.apply { cells.forEach { (idx, v) -> this[idx] = v } }

    private fun step(idx: Int, value: TangoCell, evidence: Set<Int>, row: Int? = null, col: Int? = null) =
        TangoStep.Deduction(TangoStepRung.COUNT_SQUEEZE, idx, value, evidence, row, col, options = null)

    @Test
    fun `a sun here would force three moons in a row so the cell is a moon`() {
        val puzzle = board()

        val steps = TangoCountSqueezeRule.forced(puzzle, grid(puzzle, 1 to SUN, 5 to SUN))

        assertEquals(listOf(step(0, MOON, setOf(2, 3, 4), row = 0)), steps)
    }

    @Test
    fun `a squeeze in a column names the column`() {
        val puzzle = board()

        val steps = TangoCountSqueezeRule.forced(puzzle, grid(puzzle, 6 to MOON, 30 to MOON))

        assertEquals(listOf(step(0, SUN, setOf(12, 18, 24), col = 0)), steps)
    }

    @Test
    fun `a forced fill that breaks an opposite marker squeezes the cell`() {
        val puzzle = board(6, TangoMarker(4, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        val steps = TangoCountSqueezeRule.forced(puzzle, grid(puzzle, 0 to SUN, 2 to SUN))

        assertEquals(listOf(step(1, MOON, setOf(3, 4, 5), row = 0), step(3, MOON, setOf(1, 4, 5), row = 0)), steps)
    }

    @Test
    fun `a forced fill that breaks an equal marker squeezes the cell`() {
        val puzzle = board(6, TangoMarker(2, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))

        val steps = TangoCountSqueezeRule.forced(puzzle, grid(puzzle, 0 to SUN, 2 to SUN))

        assertEquals(
            listOf(step(1, MOON, setOf(3, 4, 5), row = 0), step(4, MOON, setOf(1, 3, 5), row = 0), step(5, MOON, setOf(1, 3, 4), row = 0)),
            steps,
        )
    }

    @Test
    fun `a what-if on the last empty cell of a line has no fill to show`() {
        val puzzle = board(6, TangoMarker(4, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        val steps = TangoCountSqueezeRule.forced(puzzle, grid(puzzle, 0 to SUN, 1 to SUN, 2 to MOON, 3 to SUN, 4 to MOON))

        assertEquals(listOf(step(5, SUN, emptySet(), row = 0)), steps)
    }

    @Test
    fun `a what-if that only breaks the plain count yields nothing`() {
        val puzzle = board()

        assertTrue(TangoCountSqueezeRule.forced(puzzle, grid(puzzle, 0 to SUN, 2 to SUN, 4 to SUN)).isEmpty())
    }

    @Test
    fun `a cell with no count pressure yields nothing`() {
        val puzzle = board()

        assertTrue(TangoCountSqueezeRule.forced(puzzle, grid(puzzle, 0 to SUN, 1 to MOON)).isEmpty())
    }

    @Test
    fun `every forced cell is sound on the blanked fixture sweep`() {
        val solution = TangoTestSupport.solution
        val steps = sweep().flatMap { grid -> TangoCountSqueezeRule.forced(TangoTestSupport.sample, grid).map { grid to it } }

        assertTrue(steps.isNotEmpty())
        for ((grid, step) in steps) {
            assertEquals(solution[step.idx], step.value)
            assertNull(grid[step.idx])
            assertTrue(step.evidence.all { grid[it] == null } && step.idx !in step.evidence)
        }
    }

    @Test
    fun `repeated calls agree and leave the grid untouched`() {
        for (grid in sweep()) {
            val before = grid.toList()
            val first = TangoCountSqueezeRule.forced(TangoTestSupport.sample, grid)

            assertEquals(first, TangoCountSqueezeRule.forced(TangoTestSupport.sample, grid))
            assertEquals(before, grid)
        }
    }

    /** The fixture solution with 0..n cells blanked in a fixed pseudo-random order, over several seeds. */
    private fun sweep(): List<List<TangoCell?>> {
        val solution = TangoTestSupport.solution
        return (1..8).flatMap { seed ->
            val order = solution.indices.shuffled(Random(seed))
            (0..solution.size).map { blanked ->
                solution.toMutableList<TangoCell?>().apply { order.take(blanked).forEach { this[it] = null } }
            }
        }
    }
}
