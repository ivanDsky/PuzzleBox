package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line-lock rung (spec A9, ticket 06) on crafted boards where singles stall: a region
 * locked into one line closes the rest of the line, and its dual, a line locked into one
 * region closes the rest of the region. Both boards are unique and solve on singles + line lock.
 */
class LineLockRungTest {

    private val singlesOnly = QueensLadder(listOf(SinglesRung))
    private val withLineLock = QueensLadder(listOf(SinglesRung, LineLockRung))

    /** Region 3 (cells 19, 24) lies in column 4 alone, so column 4's cells 4, 9, 14 close. */
    private fun regionLocksColumn(): QueensPuzzle = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 1, 1, 1,
            0, 0, 1, 1, 1,
            2, 0, 1, 1, 1,
            2, 2, 1, 1, 3,
            4, 4, 4, 4, 3,
        ),
        solution = QueensTestSupport.path(1, 8, 10, 19, 22),
    )

    /** Column 2 lies in region 1 alone, so region 1's other cells close. */
    private fun columnLocksRegion(): QueensPuzzle = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 1, 1, 2,
            0, 0, 1, 1, 2,
            3, 3, 1, 1, 1,
            3, 1, 1, 1, 4,
            3, 1, 1, 4, 4,
        ),
        solution = QueensTestSupport.path(1, 9, 12, 15, 23),
    )

    /** Ticket 05's staircase: every queen is a single in turn (solution 0, 7, 14, 16, 23). */
    private fun staircase5(): QueensPuzzle = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 1, 1, 1, 1,
            1, 1, 1, 2, 2,
            1, 2, 2, 2, 2,
            1, 3, 2, 3, 3,
            1, 4, 2, 4, 3,
        ),
        solution = QueensTestSupport.path(0, 7, 14, 16, 23),
    )

    private fun start(puzzle: QueensPuzzle) = QueensCandidates.start(puzzle, emptySet())

    @Test
    fun `the rung is line-lock at Medium`() {
        assertEquals("line-lock", LineLockRung.id)
        assertEquals(Difficulty.Level.Medium, LineLockRung.level)
        assertEquals("queens.hint.rung.line-lock", LineLockRung.captionKey)
    }

    @Test
    fun `a region locked into a column excludes the rest of that column`() {
        val puzzle = regionLocksColumn()
        assertNull(SinglesRung.step(puzzle, start(puzzle)))
        assertEquals(
            QueensDeduction.Exclude(HintReason(HintReason.Kind.COLUMN, 4, LineLockRung), setOf(4, 9, 14)),
            LineLockRung.step(puzzle, start(puzzle)),
        )
    }

    @Test
    fun `a column locked into a region excludes the rest of that region`() {
        val puzzle = columnLocksRegion()
        assertNull(SinglesRung.step(puzzle, start(puzzle)))
        assertEquals(
            QueensDeduction.Exclude(HintReason(HintReason.Kind.COLUMN, 2, LineLockRung), setOf(3, 8, 13, 14, 16, 18, 21)),
            LineLockRung.step(puzzle, start(puzzle)),
        )
    }

    @Test
    fun `line lock solves boards where singles stall and labels them Medium`() {
        listOf(regionLocksColumn(), columnLocksRegion()).forEach { puzzle ->
            assertFalse(singlesOnly.run(puzzle).solved)
            val run = withLineLock.run(puzzle)
            assertTrue("$run", run.solved)
            assertEquals(LineLockRung, run.hardest)
            assertEquals(Difficulty.Level.Medium, Difficulty(run.hardest!!).level)
        }
    }

    @Test
    fun `every line-lock step excludes open cells and chains to a placement`() {
        listOf(regionLocksColumn(), columnLocksRegion()).forEach { puzzle ->
            var candidates = start(puzzle)
            var pendingExclude = false
            while (true) {
                val step = withLineLock.next(puzzle, candidates) ?: break
                candidates = when (step) {
                    is QueensDeduction.Exclude -> {
                        assertEquals(LineLockRung, step.unit.rung)
                        assertTrue("$step", step.cells.isNotEmpty() && step.cells.all(candidates::isOpen))
                        pendingExclude = true
                        candidates.exclude(step.cells)
                    }
                    is QueensDeduction.Place -> {
                        pendingExclude = false
                        candidates.place(puzzle, step.cell)
                    }
                }
            }
            assertFalse("an exclusion did not chain to a placement", pendingExclude)
            assertEquals(puzzle.solution!!.map { it.idx }.toSet(), candidates.queens)
        }
    }

    @Test
    fun `the rung returns null where singles apply`() {
        val puzzle = staircase5()
        val candidates = QueensCandidates.start(puzzle, setOf(0, 7, 14))
        assertNotNull(SinglesRung.step(puzzle, candidates))
        assertNull(LineLockRung.step(puzzle, candidates))
    }

    @Test
    fun `a solved board has no line lock`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertNull(LineLockRung.step(puzzle, QueensCandidates.start(puzzle, setOf(1, 7, 8, 14))))
    }
}
