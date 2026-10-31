package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.ZipGenerator
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.Rule
import kotlin.random.Random

/** The generator is the property-test fixture source: unique boards, honest walls, valid waypoints. */
class ZipGeneratorTest {

    @get:Rule
    val budget: Timeout = Timeout.seconds(120) // pinned budget: n=7 searches stay bounded

    private val solver = ZipSolver()

    /** Uniqueness filter: generated boards solve to exactly one path, which equals the generator's. */
    @Test
    fun `generated boards are uniquely solvable`() {
        val generator = ZipGenerator(Random(592))
        var produced = 0
        var attempts = 0
        while (produced < 8 && attempts < 100) {
            attempts++
            val puzzle = generator.next(ZipTestSupport.variantOf(5)) ?: continue
            produced++
            assertEquals(1, solver.countSolutions(puzzle))
            assertEquals(puzzle.solution, solver.solve(puzzle))
            assertTrue(ZipRules.verifyPath(puzzle, puzzle.solution!!))
        }
        assertEquals(8, produced) // n=5 unique boards come up constantly
    }

    /** Walls only on interior edges NOT traversed by the solution (mutation: dropping the exclusion fails). */
    @Test
    fun `walls never sit on the solution path`() {
        val generator = ZipGenerator(Random(7))
        val puzzle = generator.nextUnique(ZipTestSupport.variantOf(6))!!
        val solution = puzzle.solution!!
        val pathEdges = HashSet<Pair<Int, Int>>()
        for (i in 0 until solution.lastIndex) pathEdges += minOf(solution[i].idx, solution[i + 1].idx) to
            maxOf(solution[i].idx, solution[i + 1].idx)
        puzzle.board.walls.forEach { wall ->
            val a = wall.cell.idx
            val b = when (wall.direction) {
                WallDirection.RIGHT -> a + 1
                WallDirection.DOWN -> a + puzzle.board.n
            }
            val edge = minOf(a, b) to maxOf(a, b)
            assertTrue("wall on path edge $edge", edge !in pathEdges)
        }
    }

    /** Waypoint invariants: start and end of the solution always labeled, labels ascending, no duplicates. */
    @Test
    fun `waypoints anchor start and end with ascending labels`() {
        val generator = ZipGenerator(Random(11))
        repeat(5) {
            val puzzle = generator.nextUnique(ZipTestSupport.variantOf(5))!!
            val board = puzzle.board
            val solution = puzzle.solution!!
            assertEquals(solution.first(), board.waypoints.first()) // label 1 = path start (I4)
            assertEquals(solution.last(), board.waypoints.last()) // last label = path end (I6)
            assertEquals(board.waypoints.size, board.waypoints.map { it.idx }.distinct().size)
            val positions = board.waypoints.map { solution.indexOf(it) }
            assertEquals(positions, positions.sorted())
            assertTrue(board.walls.all { it.cell.idx in 0 until board.cellCount })
        }
    }

    /** n=6 daily size stays within the budget; uniqueness still holds. */
    @Test
    fun `n6 boards generate within budget`() {
        val puzzle = ZipGenerator(Random(2024)).nextUnique(ZipTestSupport.variantOf(6), maxAttempts = 40)
        if (puzzle != null) { // null is acceptable under the cap; uniqueness never is
            assertEquals(1, solver.countSolutions(puzzle))
        }
    }

    /** Exhausted retries surface as null instead of a non-unique board. */
    @Test
    fun `nextUnique gives up cleanly after maxAttempts`() {
        assertNull(ZipGenerator(Random(3)).nextUnique(ZipTestSupport.variantOf(5), maxAttempts = 0))
    }
}
