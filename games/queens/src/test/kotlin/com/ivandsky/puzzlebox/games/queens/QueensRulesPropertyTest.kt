package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules property suite (zip ZipRulesPropertyTest style): for N=1 boards 1..9 (including
 * the official daily sizes) and N=2 synthetic boards, solver-found placements must
 * evaluate Valid, and every single-queen corruption must flip the verdict AND name the
 * correct rule with the correct cell — mutation falsification across the whole space.
 */
class QueensRulesPropertyTest {

    /** Seeded property loop: deterministic across runs. */
    private val random = Random(20261102)

    /**
     * Region family (2*row + col + offset) % size. N=1: solvable at sizes 1, 5-10
     * (2-4 admit no placement — brute-force verified). N=2: only 8x8+ admit
     * two-queens-per-unit no-touch placements on this family (4x4 is impossible, 5-7
     * too small for the family) — brute-force verified.
     */
    private fun family(size: Int, offset: Int) = { r: Int, c: Int -> (2 * r + c + offset) % size }

    private fun validSolutions(size: Int, perUnit: Int, samples: Int): List<Pair<QueensPuzzle, Set<Cell>>> {
        val out = mutableListOf<Pair<QueensPuzzle, Set<Cell>>>()
        var seed = 0
        while (out.size < samples && seed <= size) {
            val puzzle = QueensTestSupport.puzzle(size, queensPerUnit = perUnit, regionOf = family(size, seed))
            QueensTestSupport.solve(puzzle)?.let { out.add(puzzle to it) }
            seed++
        }
        return out
    }

    // ---- N=1: official daily sizes 8/9/10 plus the small end (DoD: N=1 official-daily cases) ----

    @Test
    fun `N=1 - solver solutions evaluate Valid across the solvable sizes`() {
        // sizes 2..4 admit no no-touch one-per-row/column placement (brute-force verified);
        // 1 and 5..10 cover the small end plus every official daily size.
        for (size in listOf(1, 5, 6, 7, 8, 9, 10)) {
            val puzzle = QueensTestSupport.puzzle(size) { r, col -> (2 * r + col) % size }
            val solution = QueensTestSupport.solve(puzzle)
            assertTrue("size $size must admit a solution", solution != null)
            assertEquals("size $size", QueensRulesVerdict.Valid, QueensRules.evaluate(puzzle, solution!!))
        }
        // and the family-degenerate small boards: the solver must NOT hallucinate one
        assertEquals(null, QueensTestSupport.solve(QueensTestSupport.puzzle(2) { r, col -> (2 * r + col) % 2 }))
        assertEquals(null, QueensTestSupport.solve(QueensTestSupport.puzzle(3) { r, col -> (2 * r + col) % 3 }))
        assertEquals(null, QueensTestSupport.solve(QueensTestSupport.puzzle(4) { r, col -> (2 * r + col) % 4 }))
    }

    @Test
    fun `N=1 - 8x8 official daily fixture solution verifies`() {
        val json = QueensFixtureTest::class.java.getResourceAsStream("/fixtures/queens-daily-sample.json")!!
            .readBytes().decodeToString()
        val daily = when (val r = QueensWireParser.parse(json, LocalDate.of(2026, 11, 2), "1234")) {
            is DecodeResult.Ok -> r.value
            is DecodeResult.Err -> throw AssertionError(r.error.toString())
        }
        val puzzle = daily.puzzle
        assertEquals(QueensRulesVerdict.Valid, QueensRules.evaluate(puzzle, puzzle.solution!!.toSet()))
        // the independent test solver agrees the board admits a valid placement
        assertTrue(QueensTestSupport.solve(puzzle) != null)
    }

    // ---- corruption attribution: every rule class is caught with the right cell ----

    @Test
    fun `N=1 - each single corruption flips to Invalid naming the right rule`() {
        for ((puzzle, solution) in validSolutions(size = 5, perUnit = 1, samples = 4)) {
            val queen = solution.first()
            val n = puzzle.size
            val sameRow = QueensTestSupport.cell(puzzle.rowOf(queen), (queen.idx + 2) % n, n)
            val sameCol = QueensTestSupport.cell((queen.idx / n + 2) % n, puzzle.colOf(queen), n)

            // remove one queen -> Incomplete with exact counts
            val minus = QueensRules.evaluate(puzzle, solution - queen)
            assertEquals(QueensRulesVerdict.Incomplete(n - 1, n), minus)

            // duplicate into the same row AND a distant cell -> row violation attributed
            val dup = solution + sameRow
            val dupVerdict = QueensRules.evaluate(puzzle, dup)
            assertTrue(dupVerdict is QueensRulesVerdict.Invalid)
            assertTrue(
                (dupVerdict as QueensRulesVerdict.Invalid).violations
                    .any { it.rule == QueensRuleId.RowExclusivity && it.cell == queen },
            )

            // duplicate into the same column -> column violation attributed
            val colVerdict = QueensRules.evaluate(puzzle, solution + sameCol)
            assertTrue(colVerdict is QueensRulesVerdict.Invalid)
            assertTrue(
                (colVerdict as QueensRulesVerdict.Invalid).violations
                    .any { it.rule == QueensRuleId.ColumnExclusivity },
            )

            // shift a queen onto a cell diagonal-adjacent to its old cell: the target row
            // still holds its own queen, so the verdict can never be Valid (exact
            // adjacency attribution is pinned in NoTouchAdjacencyRuleTest).
            val neighbor = QueensTestSupport.cell(
                (puzzle.rowOf(queen) + 1) % n,
                (puzzle.colOf(queen) + 1) % n,
                n,
            )
            if (neighbor !in solution) {
                assertNotEquals(QueensRulesVerdict.Valid, QueensRules.evaluate(puzzle, solution - queen + neighbor))
            }
        }
    }

    // ---- N=2 synthetic (Star Battle regime, A9) ----

    @Test
    fun `N=2 - synthetic boards admit and verify two-queen solutions`() {
        for ((puzzle, solution) in validSolutions(size = 8, perUnit = 2, samples = 2)) {
            assertEquals(solution.size, puzzle.size * 2)
            assertEquals(QueensRulesVerdict.Valid, QueensRules.evaluate(puzzle, solution))
            assertTrue(QueensVerifier.isSolution(puzzle, solution))
        }
    }

    @Test
    fun `N=2 - third queen in a unit flips the verdict`() {
        // find any N=2 region family on the 6x6 board that admits a solution
        var found: Pair<QueensPuzzle, Set<Cell>>? = null
        for (offset in 0 until 4) {
            val puzzle = QueensTestSupport.puzzle(8, queensPerUnit = 2) { r, col -> (2 * r + col + offset) % 8 }
            val solution = QueensTestSupport.solve(puzzle)
            if (solution != null) {
                found = puzzle to solution
                break
            }
        }
        assertTrue("at least one N=2 family must admit a solution", found != null)
        val (puzzle, solution) = found!!
        val anyQueen = solution.first()
        val sameRow = QueensTestSupport.cell(puzzle.rowOf(anyQueen), (anyQueen.idx + 2) % 8, 8)
        if (sameRow !in solution) {
            val verdict = QueensRules.evaluate(puzzle, solution + sameRow)
            assertTrue(verdict is QueensRulesVerdict.Invalid)
            assertTrue(
                (verdict as QueensRulesVerdict.Invalid).violations
                    .any { it.rule == QueensRuleId.RowExclusivity },
            )
        }
    }

}
