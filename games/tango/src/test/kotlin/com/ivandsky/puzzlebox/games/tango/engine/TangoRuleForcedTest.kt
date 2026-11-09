package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `forced` is the full list behind each legacy rule's `deduction`; its head must stay the old answer. */
class TangoRuleForcedTest {

    private val puzzle = TangoTestSupport.sample
    private val solution = TangoTestSupport.solution

    private class Rule(
        val name: String,
        val forced: (TangoPuzzle, List<TangoCell?>) -> List<TangoHint.Forced>,
        val deduction: (TangoPuzzle, List<TangoCell?>) -> TangoHint.Forced?,
    )

    private val rules = listOf(
        Rule("marker", TangoMarkerRule::forced, TangoMarkerRule::deduction),
        Rule("triple", TangoTripleRule::forced, TangoTripleRule::deduction),
        Rule("count", TangoCountRule::forced, TangoCountRule::deduction),
        Rule("line", TangoLineRule::forced, TangoLineRule::deduction),
    )

    /** Solution with 0..n cells blanked in a fixed pseudo-random order, plus empty and full grids. */
    private fun sweep(): List<List<TangoCell?>> {
        val order = solution.indices.shuffled(Random(15))
        return (0..solution.size).map { blanked ->
            val grid = solution.toMutableList<TangoCell?>()
            order.take(blanked).forEach { grid[it] = null }
            grid
        }
    }

    @Test
    fun `deduction is the head of forced on every swept grid`() {
        for (rule in rules) for (grid in sweep()) {
            assertEquals(rule.name, rule.forced(puzzle, grid).firstOrNull(), rule.deduction(puzzle, grid))
        }
    }

    @Test
    fun `every forced cell is sound against the solution`() {
        for (rule in rules) for (grid in sweep()) for (hint in rule.forced(puzzle, grid)) {
            assertEquals(rule.name, solution[hint.idx], hint.value)
            assertNull(rule.name, grid[hint.idx])
        }
    }

    @Test
    fun `full grid yields nothing`() {
        for (rule in rules) {
            assertTrue(rule.name, rule.forced(puzzle, solution).isEmpty())
            assertNull(rule.name, rule.deduction(puzzle, solution))
        }
    }

    @Test
    fun `a board with nothing forced yields nothing`() {
        val bare = puzzle.copy(givens = emptyMap(), markers = emptyList())
        val grid = List<TangoCell?>(bare.cellCount) { null }
        for (rule in rules) {
            assertTrue(rule.name, rule.forced(bare, grid).isEmpty())
            assertNull(rule.name, rule.deduction(bare, grid))
        }
    }

    @Test
    fun `count rule lists every gap of a full line`() {
        val bare = puzzle.copy(givens = emptyMap(), markers = emptyList())
        val grid = MutableList<TangoCell?>(bare.cellCount) { null }
        val row = listOf(TangoCell.SUN, TangoCell.SUN, TangoCell.MOON, TangoCell.SUN)
        row.forEachIndexed { i, cell -> grid[i] = cell }

        val all = TangoCountRule.forced(bare, grid)

        val why = TangoHint.Why.LineFull(row = 0, col = null, fullOf = TangoCell.SUN)
        assertEquals(
            listOf(TangoHint.Forced(4, TangoCell.MOON, why), TangoHint.Forced(5, TangoCell.MOON, why)),
            all,
        )
        assertEquals(all.first(), TangoCountRule.deduction(bare, grid))
    }

    @Test
    fun `triple rule lists every window in scan order`() {
        val bare = puzzle.copy(givens = emptyMap(), markers = emptyList())
        val grid = MutableList<TangoCell?>(bare.cellCount) { null }
        listOf(0, 1, 4, 5).forEach { grid[it] = TangoCell.SUN }

        val all = TangoTripleRule.forced(bare, grid)

        assertEquals(
            listOf(
                TangoHint.Forced(2, TangoCell.MOON, TangoHint.Why.ThreeInARow(0, 1)),
                TangoHint.Forced(3, TangoCell.MOON, TangoHint.Why.ThreeInARow(4, 5)),
            ),
            all,
        )
        assertEquals(all.first(), TangoTripleRule.deduction(bare, grid))
    }
}
