package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport
import com.ivandsky.puzzlebox.games.zip.path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ZipSearch.bounded] polls its [Budget] inside the DFS, so the editor's 1.5 s analysis
 * bound holds on the largest open boards, and [Budget.Unlimited] leaves results unchanged.
 */
class ZipSearchBudgetTest {

    /** Open 3×3, corner to opposite corner: exactly the row-wise and column-wise snakes. */
    private val twoPathBoard = ZipPuzzle(
        ZipTestSupport.variantOf(3).id,
        ZipBoard(3, emptySet(), listOf(Cell(0), Cell(8))),
        null,
    )

    @Test
    fun `a two-path board returns both paths`() {
        val outcome = ZipSearch.bounded(twoPathBoard, limit = 2, budget = Budget.Unlimited)

        val solutions = (outcome as ZipSearchOutcome.Done).solutions
        assertEquals(
            setOf(path(0, 1, 2, 5, 4, 3, 6, 7, 8), path(0, 3, 6, 7, 4, 1, 2, 5, 8)),
            solutions.toSet(),
        )
        assertEquals(2, solutions.size)
    }

    @Test
    fun `the limit caps the paths returned`() {
        val outcome = ZipSearch.bounded(twoPathBoard, limit = 1, budget = Budget.Unlimited)

        assertEquals(1, (outcome as ZipSearchOutcome.Done).solutions.size)
    }

    @Test
    fun `an unlimited budget keeps the forced board's single path`() {
        val puzzle = ZipTestSupport.n5Puzzle()

        val outcome = ZipSearch.bounded(puzzle, limit = 2, budget = Budget.Unlimited)

        assertEquals(ZipSearchOutcome.Done(listOf(ZipTestSupport.n5Solution)), outcome)
    }

    @Test
    fun `an already-exhausted budget returns Exhausted without searching`() {
        var polls = 0
        val spent = Budget { polls++; true }

        val outcome = ZipSearch.bounded(twoPathBoard, limit = 2, budget = spent)

        assertEquals(ZipSearchOutcome.Exhausted, outcome)
        assertEquals(1, polls)
    }

    @Test
    fun `a budget spent mid-search returns Exhausted`() {
        var polls = 0
        val spentAfterFirstPoll = Budget { ++polls > 1 }

        val outcome = ZipSearch.bounded(ZipWorstCaseFixtures.adjacentCorners, limit = 2, budget = spentAfterFirstPoll)

        assertEquals(ZipSearchOutcome.Exhausted, outcome)
        assertEquals(2, polls)
    }

    @Test
    fun `a node cap stops the search with no clock and no cancel`() {
        val capped = ZipSearch.bounded(ZipWorstCaseFixtures.adjacentCorners, limit = 2, budget = Budget.Unlimited, maxNodes = 1_000)
        val roomy = ZipSearch.bounded(twoPathBoard, limit = 2, budget = Budget.Unlimited, maxNodes = 1_000)

        assertEquals(ZipSearchOutcome.Exhausted, capped)
        assertEquals(2, (roomy as ZipSearchOutcome.Done).solutions.size)
    }

    @Test
    fun `a board parity rules out answers Done with no paths before the DFS`() {
        var polls = 0
        val counting = Budget { polls++; false }
        val diagonal2x2 = ZipPuzzle(
            ZipTestSupport.variantOf(3).id,
            ZipBoard(2, emptySet(), listOf(Cell(0), Cell(3))),
            null,
        )

        val outcome = ZipSearch.bounded(diagonal2x2, limit = 2, budget = counting)

        assertEquals(ZipSearchOutcome.Done(emptyList()), outcome)
        assertEquals(1, polls)
    }

    @Test
    fun `the opposite-corner worst case is ruled out by parity at once`() {
        val start = System.nanoTime()

        val outcome = ZipSearch.bounded(
            ZipWorstCaseFixtures.oppositeCorners,
            limit = 2,
            budget = Budget.within(BOUND_MS, System::nanoTime),
        )

        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI
        assertEquals(ZipSearchOutcome.Done(emptyList()), outcome)
        assertTrue("took $elapsedMs ms", elapsedMs <= PARITY_BOUND_MS)
    }

    /**
     * Solvable but wide open: the search may not find two paths in time, so Exhausted is
     * allowed. The budget sits well under the bound so a loaded runner still makes it.
     */
    @Test
    fun `the adjacent-corner worst case settles within the editor budget`() {
        val start = System.nanoTime()

        val outcome = ZipSearch.bounded(
            ZipWorstCaseFixtures.adjacentCorners,
            limit = 2,
            budget = Budget.within(SEARCH_BUDGET_MS, System::nanoTime),
        )

        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI
        assertTrue("took $elapsedMs ms", elapsedMs <= BOUND_MS)
        assertTrue(outcome is ZipSearchOutcome.Done || outcome == ZipSearchOutcome.Exhausted)
    }

    private companion object {
        const val BOUND_MS = 1_500L
        const val SEARCH_BUDGET_MS = 400L
        const val PARITY_BOUND_MS = 100L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
