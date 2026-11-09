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

class TangoSignCountRuleTest {

    private fun board(n: Int = 6, vararg markers: TangoMarker): TangoPuzzle =
        TangoPuzzle(VariantId("tango/n$n"), n, emptyMap(), markers.toList())

    private fun grid(puzzle: TangoPuzzle, vararg cells: Pair<Int, TangoCell>): List<TangoCell?> =
        MutableList<TangoCell?>(puzzle.cellCount) { null }.apply { cells.forEach { (idx, v) -> this[idx] = v } }

    private fun step(idx: Int, value: TangoCell, evidence: Set<Int>, row: Int? = null, col: Int? = null) =
        TangoStep.Deduction(TangoStepRung.SIGN_COUNT, idx, value, evidence, row, col, options = null)

    @Test
    fun `an opposite pair uses up the suns of a row so the rest is moon`() {
        val puzzle = board(6, TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        val steps = TangoSignCountRule.forced(puzzle, grid(puzzle, 0 to SUN, 3 to SUN))

        assertEquals(listOf(step(4, MOON, setOf(1, 2), row = 0), step(5, MOON, setOf(1, 2), row = 0)), steps)
    }

    @Test
    fun `an opposite pair in a column names the column`() {
        val puzzle = board(6, TangoMarker(6, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE))

        val steps = TangoSignCountRule.forced(puzzle, grid(puzzle, 0 to MOON, 18 to MOON))

        assertEquals(listOf(step(24, SUN, setOf(6, 12), col = 0), step(30, SUN, setOf(6, 12), col = 0)), steps)
    }

    @Test
    fun `an equal pair cannot take the value with one left so both are the other`() {
        val puzzle = board(6, TangoMarker(4, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))

        val steps = TangoSignCountRule.forced(puzzle, grid(puzzle, 0 to SUN, 1 to MOON, 2 to SUN))

        assertEquals(listOf(step(4, MOON, setOf(4, 5), row = 0), step(5, MOON, setOf(4, 5), row = 0)), steps)
    }

    @Test
    fun `an opposite pair counts toward the equal pair and lights both pairs`() {
        val puzzle = board(
            6,
            TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
            TangoMarker(3, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        )

        val steps = TangoSignCountRule.forced(puzzle, grid(puzzle, 2 to SUN))

        assertEquals(listOf(step(3, MOON, setOf(0, 1, 3, 4), row = 0), step(4, MOON, setOf(0, 1, 3, 4), row = 0)), steps)
    }

    @Test
    fun `an opposite pair whose line count leaves both values open yields nothing`() {
        val puzzle = board(6, TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        assertTrue(TangoSignCountRule.forced(puzzle, grid(puzzle, 0 to SUN)).isEmpty())
    }

    @Test
    fun `an opposite pair with a known end is sign and is not counted`() {
        val puzzle = board(6, TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        assertTrue(TangoSignCountRule.forced(puzzle, grid(puzzle, 1 to SUN, 3 to SUN)).isEmpty())
    }

    @Test
    fun `an equal pair with two of each value left yields nothing`() {
        val puzzle = board(6, TangoMarker(4, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))

        assertTrue(TangoSignCountRule.forced(puzzle, grid(puzzle, 0 to SUN, 1 to MOON)).isEmpty())
    }

    @Test
    fun `a used-up value with no opposite pair is full line and yields nothing`() {
        val puzzle = board()

        assertTrue(TangoSignCountRule.forced(puzzle, grid(puzzle, 0 to SUN, 2 to SUN, 4 to SUN)).isEmpty())
    }

    @Test
    fun `every forced cell is sound on the blanked fixture sweep`() {
        val solution = TangoTestSupport.solution
        val steps = sweep().flatMap { grid -> TangoSignCountRule.forced(TangoTestSupport.sample, grid).map { grid to it } }

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
            val first = TangoSignCountRule.forced(TangoTestSupport.sample, grid)

            assertEquals(first, TangoSignCountRule.forced(TangoTestSupport.sample, grid))
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
