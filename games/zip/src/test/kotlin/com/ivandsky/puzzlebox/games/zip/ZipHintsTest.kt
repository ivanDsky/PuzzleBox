package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.ZipHint
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smallest-safe-help: one step when alive, backtrack to the longest solvable prefix when dead (F2 §5 §3). */
class ZipHintsTest {

    private val hints = com.ivandsky.puzzlebox.games.zip.engine.ZipHints()

    /** Alive trace: Step to the next cell of the unique continuation — one cell, never the rest. */
    @Test
    fun `alive trace gets a single-cell step`() {
        val puzzle = ZipTestSupport.n5Puzzle()
        val prefix = ZipTestSupport.path(0, 1, 2)
        val hint = hints.hint(ZipState(puzzle, prefix))
        assertEquals(ZipHint.Step(Cell(3)), hint)
    }

    @Test
    fun `empty trace steps onto the start cell`() {
        val puzzle = ZipTestSupport.n5Puzzle()
        val hint = hints.hint(ZipState(puzzle, emptyList()))
        assertEquals(ZipHint.Step(Cell(0)), hint)
    }

    /** Dead trace on the branchy board: [0,1,2,3,8] cannot finish; longest solvable prefix is [0,1,2,3]. */
    @Test
    fun `dead trace gets a backtrack to the longest solvable prefix`() {
        val puzzle = ZipTestSupport.n5BranchyPuzzle()
        val dead = ZipTestSupport.path(0, 1, 2, 3, 8)
        val hint = hints.hint(ZipState(puzzle, dead))
        assertEquals(ZipHint.Backtrack(3), hint) // keep path[0..3], trim cell 8
    }

    /** Hinting never reads the stored solution: strip it and the answers are identical. */
    @Test
    fun `hints work without a stored solution`() {
        val withSolution = ZipTestSupport.n5BranchyPuzzle()
        val blind = withSolution.copy(solution = null)
        val dead = ZipTestSupport.path(0, 1, 2, 3, 8)
        assertEquals(
            hints.hint(ZipState(withSolution, dead)),
            hints.hint(ZipState(blind, dead)),
        )
        assertEquals(
            hints.hint(ZipState(withSolution, ZipTestSupport.path(0, 1))),
            hints.hint(ZipState(blind, ZipTestSupport.path(0, 1))),
        )
    }

    /** After the free trim the caller re-hints: the trimmed prefix is alive and gets a Step. */
    @Test
    fun `the free trim then re-hint yields a step`() {
        val puzzle = ZipTestSupport.n5BranchyPuzzle()
        val dead = ZipTestSupport.path(0, 1, 2, 3, 8)
        val backtrack = hints.hint(ZipState(puzzle, dead)) as ZipHint.Backtrack
        val trimmed = dead.take(backtrack.trimTo + 1)
        assertTrue(ZipRules.verifyPath(puzzle, trimmed))
        val step = hints.hint(ZipState(puzzle, trimmed))
        assertEquals(ZipHint.Step(Cell(4)), step)
    }
}
