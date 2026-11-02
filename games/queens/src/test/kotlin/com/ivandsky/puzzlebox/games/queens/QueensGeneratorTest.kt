package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import kotlin.random.Random

/** The generator is the property-test fixture source: unique boards, valid regions (A13 hard gate). */
class QueensGeneratorTest {

    @get:Rule
    val budget: Timeout = Timeout.seconds(300) // pinned budget: n8-n10 searches stay bounded

    private val solver = QueensSolver()

    /** Uniqueness filter: generated boards solve to exactly one placement, which equals the generator's. */
    @Test
    fun `generated boards are uniquely solvable - official daily size n8`() {
        val generator = QueensGenerator(Random(592))
        var produced = 0
        var attempts = 0
        while (produced < 4 && attempts < 60) {
            attempts++
            val puzzle = generator.next(8) ?: continue
            produced++
            assertEquals(1, solver.countSolutions(puzzle))
            assertEquals(puzzle.solution, solver.solve(puzzle))
            assertEquals(QueensRulesVerdict.Valid, QueensRules.evaluate(puzzle, puzzle.solution!!.toSet()))
        }
        assertEquals(4, produced) // n8 unique boards come up regularly
    }

    /** A9: the SAME generator covers N=2 (Star Battle shape) at synthetic sizes. */
    @Test
    fun `generated boards are uniquely solvable - N=2 synthetic n8`() {
        val generator = QueensGenerator(Random(42))
        var produced = 0
        var attempts = 0
        while (produced < 2 && attempts < 60) {
            attempts++
            val puzzle = generator.next(8, queensPerUnit = 2) ?: continue
            produced++
            assertEquals(1, solver.countSolutions(puzzle))
            assertEquals(puzzle.solution, solver.solve(puzzle))
        }
        assertEquals(2, produced)
    }

    /** Region invariants: exactly size regions, all nonempty, exactly one queen per region (N=1). */
    @Test
    fun `regions partition the board with exactly one queen each`() {
        val generator = QueensGenerator(Random(11))
        repeat(3) {
            val puzzle = generator.nextUnique(8)!!
            val n = puzzle.size
            assertEquals(n, puzzle.regions.distinct().size)
            for (r in 0 until n) {
                val regionCells = puzzle.regions.count { it == r }
                assertTrue("region $r empty", regionCells > 0)
                assertEquals(
                    "region $r holds ${puzzle.solution!!.count { puzzle.regionOf(it) == r }} queens",
                    1,
                    puzzle.solution!!.count { puzzle.regionOf(it) == r },
                )
            }
        }
    }

    /** Independent-solution agreement (A13 oracle INPUT): a second solve matches the shipped solution. */
    @Test
    fun `independent solve matches the shipped solution`() {
        val puzzle = QueensGenerator(Random(2024)).nextUnique(9)!!
        assertEquals(puzzle.solution, QueensSolver().solve(puzzle))
    }

    /** n10 (largest official size) stays within the budget; uniqueness still holds. */
    @Test
    fun `n10 boards generate within budget`() {
        val puzzle = QueensGenerator(Random(777)).nextUnique(10, maxAttempts = 40)
        if (puzzle != null) { // null is acceptable under the cap; uniqueness never is
            assertEquals(1, solver.countSolutions(puzzle))
        }
    }

    /** Exhausted retries surface as null instead of a non-unique board. */
    @Test
    fun `nextUnique gives up cleanly after maxAttempts`() {
        assertNull(QueensGenerator(Random(3)).nextUnique(8, maxAttempts = 0))
    }

    /** Determinism: same seed, same boards (golden snapshots depend on this). */
    @Test
    fun `generation is seed-deterministic`() {
        val a = QueensGenerator(Random(592)).nextUnique(8)
        val b = QueensGenerator(Random(592)).nextUnique(8)
        assertNotNull(a)
        assertEquals(a, b)
    }
}
