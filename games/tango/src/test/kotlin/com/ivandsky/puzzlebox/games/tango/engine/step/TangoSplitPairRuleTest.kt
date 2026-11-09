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

class TangoSplitPairRuleTest {

    private fun board(n: Int = 6, vararg markers: TangoMarker): TangoPuzzle =
        TangoPuzzle(VariantId("tango/n$n"), n, emptyMap(), markers.toList())

    private fun grid(puzzle: TangoPuzzle, vararg cells: Pair<Int, TangoCell>): List<TangoCell?> =
        MutableList<TangoCell?>(puzzle.cellCount) { null }.apply { cells.forEach { (idx, v) -> this[idx] = v } }

    @Test
    fun `a split gap in a row uses up the suns so the rest is moon`() {
        val puzzle = board()

        val steps = TangoSplitPairRule.forced(puzzle, grid(puzzle, 0 to SUN, 3 to MOON, 4 to SUN))

        assertEquals(
            listOf(TangoStep.Deduction(TangoStepRung.SPLIT_PAIR, 5, MOON, setOf(0, 1, 2, 3), row = 0, col = null, options = null)),
            steps,
        )
    }

    @Test
    fun `a split gap in a column names the column`() {
        val puzzle = board()

        val steps = TangoSplitPairRule.forced(puzzle, grid(puzzle, 0 to SUN, 18 to MOON, 24 to SUN))

        assertEquals(
            listOf(TangoStep.Deduction(TangoStepRung.SPLIT_PAIR, 30, MOON, setOf(0, 6, 12, 18), row = null, col = 0, options = null)),
            steps,
        )
    }

    @Test
    fun `an opposite pair counts beside the split gap and lights both its cells`() {
        val puzzle = board(8, TangoMarker(4, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        val steps = TangoSplitPairRule.forced(puzzle, grid(puzzle, 0 to SUN, 3 to MOON, 6 to SUN))

        assertEquals(
            listOf(TangoStep.Deduction(TangoStepRung.SPLIT_PAIR, 7, MOON, setOf(0, 1, 2, 3, 4, 5), row = 0, col = null, options = null)),
            steps,
        )
    }

    @Test
    fun `every rest cell of the line is forced with one shared evidence`() {
        val puzzle = board(8)

        val steps = TangoSplitPairRule.forced(puzzle, grid(puzzle, 0 to SUN, 3 to MOON, 4 to SUN, 5 to SUN))

        val evidence = setOf(0, 1, 2, 3)
        assertEquals(
            listOf(
                TangoStep.Deduction(TangoStepRung.SPLIT_PAIR, 6, MOON, evidence, row = 0, col = null, options = null),
                TangoStep.Deduction(TangoStepRung.SPLIT_PAIR, 7, MOON, evidence, row = 0, col = null, options = null),
            ),
            steps,
        )
    }

    @Test
    fun `a gap between equal ends yields nothing`() {
        val puzzle = board()

        assertTrue(TangoSplitPairRule.forced(puzzle, grid(puzzle, 0 to SUN, 3 to SUN, 4 to MOON)).isEmpty())
    }

    @Test
    fun `a split gap whose line count leaves both values open yields nothing`() {
        val puzzle = board()

        assertTrue(TangoSplitPairRule.forced(puzzle, grid(puzzle, 0 to SUN, 3 to MOON)).isEmpty())
    }

    @Test
    fun `an opposite pair alone is sign count and yields nothing`() {
        val puzzle = board(6, TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        assertTrue(TangoSplitPairRule.forced(puzzle, grid(puzzle, 3 to SUN, 4 to SUN)).isEmpty())
    }

    @Test
    fun `every forced cell is sound on the blanked fixture sweep`() {
        val solution = TangoTestSupport.solution
        val steps = sweep().flatMap { grid -> TangoSplitPairRule.forced(TangoTestSupport.sample, grid).map { grid to it } }

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
            val first = TangoSplitPairRule.forced(TangoTestSupport.sample, grid)

            assertEquals(first, TangoSplitPairRule.forced(TangoTestSupport.sample, grid))
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
