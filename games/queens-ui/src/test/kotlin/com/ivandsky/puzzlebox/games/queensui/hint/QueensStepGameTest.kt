package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStepper
import com.ivandsky.puzzlebox.games.queensui.QueensUiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what the Queens game adds to `StepHints`: the solution resolved once per puzzle inside the compute block (A19),
 * the capture-time snapshot and `lastMove`, the keys and the Spotlight. Calls the game directly with a live session.
 */
class QueensStepGameTest {

    /** 4×4, rows are regions; stored solution 1, 7, 8, 14, the other one 2, 4, 11, 13. Cell 2 is off the stored one and breaks no rule alone. */
    private val small: QueensPuzzle = QueensUiFixtures.puzzle()
    private val smallSolution: Set<Cell> = QueensUiFixtures.solutionCells.toSet()

    /** A queen of the other solution, and a cell of the stored one that the other leaves empty. */
    private val otherQueen = Cell(4)
    private val otherEmpty = Cell(8)

    /** gen-n8-q1 of the solver golden workload (unique; the ladder opens with a deduction). Solution 5, 10, 16, 28, 39, 43, 49, 62. */
    private val big: QueensPuzzle = QueensPuzzle(
        size = 8,
        regions = listOf(
            3, 4, 5, 1, 2, 0, 0, 5,
            6, 7, 1, 5, 1, 4, 2, 4,
            2, 3, 5, 1, 4, 3, 3, 7,
            7, 6, 1, 4, 3, 4, 4, 1,
            1, 1, 5, 4, 6, 4, 1, 4,
            4, 1, 4, 5, 1, 5, 7, 1,
            1, 6, 5, 5, 5, 7, 7, 4,
            6, 5, 0, 3, 5, 2, 7, 7,
        ),
        solution = listOf(5, 10, 16, 28, 39, 43, 49, 62).map(::Cell),
    )
    private val offSolution = Cell(2)

    /** One play: the live session and a counting solver seam. */
    private inner class Play(
        puzzle: QueensPuzzle,
        solve: ((QueensPuzzle) -> Set<Cell>?)? = null,
    ) {
        var session = QueensSession(ref = null, puzzle = puzzle)
        var solves = 0
        val game = if (solve == null) {
            QueensStepGame(session = { session })
        } else {
            QueensStepGame(session = { session }, solve = { solves++; solve(it) })
        }

        fun next(): QueensStep = game.capture()()

        fun place(cell: Cell) {
            session = session.withQueenPlaced(cell, QueensUiFixtures.T0)
        }
    }

    // The solution, once per puzzle (A19)

    @Test
    fun `a puzzle without a stored solution is solved once across presses`() {
        val play = Play(small.copy(solution = null), solve = { smallSolution })

        play.next()
        play.place(offSolution)
        play.next()

        assertEquals(1, play.solves)
    }

    @Test
    fun `a second puzzle in the same game is solved again`() {
        val play = Play(big.copy(solution = null), solve = { QueensStepGame.boundedSolution(it) })
        play.next()

        play.session = QueensSession(ref = null, puzzle = small.copy(solution = null))
        play.next()

        assertEquals(2, play.solves)
    }

    @Test
    fun `a puzzle with a stored solution is still searched, once across presses`() {
        val play = Play(big, solve = { QueensStepGame.boundedSolution(it) })

        play.next()
        play.place(offSolution)
        play.next()

        assertEquals(1, play.solves)
    }

    @Test
    fun `a stored solution on a two-solution board accuses neither the other solution's queen nor its cross, and reveals nothing`() {
        assertNull("precondition: the search finds two solutions", QueensStepGame.boundedSolution(small.copy(solution = null)))
        val queen = Play(small)
        queen.place(otherQueen)
        val cross = Play(small)
        cross.session = cross.session.withCrossToggled(otherEmpty, QueensUiFixtures.T0)

        for (step in listOf(queen.next(), cross.next())) {
            assertTrue("$step", step !is QueensStep.Mistake && step !is QueensStep.Reveal)
        }
    }

    @Test
    fun `a spent budget keeps the stored solution`() {
        assertEquals(big.solution!!.toSet(), QueensStepGame.boundedSolution(big, pollLimit = 0))
    }

    @Test
    fun `the solver runs in the compute block, not at capture`() {
        val play = Play(small.copy(solution = null), solve = { smallSolution })

        val block = play.game.capture()
        assertEquals(0, play.solves)

        block()
        assertEquals(1, play.solves)
    }

    @Test
    fun `the default search finds a unique puzzle's solution and a spent budget finds none`() {
        val bare = big.copy(solution = null)

        assertEquals(big.solution!!.toSet(), QueensStepGame.boundedSolution(bare))
        assertNull(QueensStepGame.boundedSolution(bare, pollLimit = 0))
    }

    @Test
    fun `a non-unique puzzle without a stored solution has none, so a legal queen is not accused`() {
        val bare = small.copy(solution = null)
        assertNull(QueensStepGame.boundedSolution(bare))
        val play = Play(bare)
        play.place(offSolution)

        val step = play.next()

        assertTrue("$step", step !is QueensStep.Mistake && step !is QueensStep.Reveal)
    }

    @Test
    fun `a spent budget accuses no merely off-solution queen, reveals nothing, and is not retried`() {
        val bare = small.copy(solution = null)
        val play = Play(bare, solve = { QueensStepGame.boundedSolution(it, pollLimit = 0) })
        play.place(offSolution)

        val step = play.next()

        assertTrue("$step", step !is QueensStep.Mistake && step !is QueensStep.Reveal)
        assertEquals(QueensStepper.next(bare, play.session.board, true, offSolution, null), step)
        play.next()
        assertEquals(1, play.solves)
    }

    // The snapshot

    @Test
    fun `the block answers for the board at capture, not the live one`() {
        val play = Play(big)
        val block = play.game.capture()

        play.place(offSolution)
        val step = block()

        assertFalse("$step", step is QueensStep.Mistake)
        assertEquals(QueensStepper.next(big, QueensBoard(), true, null, big.solution!!.toSet()), step)
    }

    @Test
    fun `auto-cross off is snapshotted with the board`() {
        val play = Play(big)
        play.session = play.session.withAutoCross(false)
        play.place(big.solution!!.first())
        val board = play.session.board
        val off = QueensStepper.next(big, board, false, big.solution!!.first(), big.solution!!.toSet())
        assertNotEquals("precondition: auto-cross changes the step", QueensStepper.next(big, board, true, big.solution!!.first(), big.solution!!.toSet()), off)

        assertEquals(off, play.next())
    }

    // lastMove

    @Test
    fun `of two equal deductions the one nearer the newest placement is shown`() {
        val solution = big.solution!!.toSet()
        val cell = big.solution!!.firstOrNull { cell ->
            val board = QueensBoard(listOf(cell), emptySet())
            val near = QueensStepper.next(big, board, true, cell, solution)
            val far = QueensStepper.next(big, board, true, null, solution)
            near is QueensStep.Deduction && far is QueensStep.Deduction && near != far
        }
        assertNotNull("precondition: some placement leaves two deductions, lastMove picking between them", cell)
        val play = Play(big)
        play.place(cell!!)

        assertEquals(QueensStepper.next(big, play.session.board, true, cell, solution), play.next())
    }

    @Test
    fun `the newest command is the last move, whichever kind it is`() {
        val play = Play(big)
        play.place(Cell(0))
        play.place(Cell(63))

        assertEquals(QueensStep.Mistake(Cell(63), QueensStep.Mistake.Kind.OFF_SOLUTION, null), play.next())

        play.session = play.session.withCrossToggled(Cell(2), QueensUiFixtures.T0)
        assertEquals(QueensStep.Mistake(Cell(0), QueensStep.Mistake.Kind.OFF_SOLUTION, null), play.next())
    }

    @Test
    fun `no history and a clear both mean no last move`() {
        val play = Play(big)
        val solution = big.solution!!.toSet()
        assertEquals(QueensStepper.next(big, QueensBoard(), true, null, solution), play.next())

        play.place(big.solution!!.last())
        play.session = play.session.withCleared(QueensUiFixtures.T0)

        assertEquals(QueensStepper.next(big, QueensBoard(), true, null, solution), play.next())
    }

    // Key, Spotlight

    @Test
    fun `only shown steps have keys`() {
        val game = Play(big).game

        assertNull(game.keyOf(QueensStep.Solved))
        assertNull(game.keyOf(QueensStep.Unavailable))
    }

    @Test
    fun `the Spotlight is drawn on the live session's puzzle`() {
        val play = Play(small)
        val mistake = QueensStep.Mistake(Cell(2), QueensStep.Mistake.Kind.OFF_SOLUTION, null)
        play.session = QueensSession(ref = null, puzzle = big)

        val spotlight = play.game.spotlight(mistake, playId = 7)

        assertEquals(big.size, spotlight?.rows)
        assertEquals(7, spotlight?.playId)
        assertNull(play.game.spotlight(QueensStep.Solved, playId = 8))
    }
}
