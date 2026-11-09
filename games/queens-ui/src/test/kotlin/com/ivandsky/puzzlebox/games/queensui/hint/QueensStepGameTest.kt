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
 * the capture-time snapshot and `lastMove`, and the erase hook. Calls the game directly with a live session.
 */
class QueensStepGameTest {

    /** 4×4, rows are regions; stored solution 1, 7, 8, 14. Cell 2 is off it and breaks no rule alone. */
    private val small: QueensPuzzle = QueensUiFixtures.puzzle()
    private val smallSolution: Set<Cell> = QueensUiFixtures.solutionCells.toSet()

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

    /** One play: the live session, a counting solver seam, and a recording erase hook. */
    private inner class Play(
        puzzle: QueensPuzzle,
        solve: ((QueensPuzzle) -> Set<Cell>?)? = null,
    ) {
        var session = QueensSession(ref = null, puzzle = puzzle)
        var solves = 0
        val erased = mutableListOf<QueensStep.Mistake>()
        val game = if (solve == null) {
            QueensStepGame(session = { session }, onEraseCulprit = { erased += it })
        } else {
            QueensStepGame(session = { session }, onEraseCulprit = { erased += it }, solve = { solves++; solve(it) })
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
    fun `a puzzle with a stored solution never calls the solver`() {
        val play = Play(small, solve = { error("must not solve") })

        play.place(offSolution)

        assertEquals(QueensStep.Mistake(offSolution, QueensStep.Mistake.Kind.OFF_SOLUTION, null), play.next())
        assertEquals(0, play.solves)
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
        val play = Play(small)
        val block = play.game.capture()

        play.place(offSolution)
        val step = block()

        assertFalse("$step", step is QueensStep.Mistake)
        assertEquals(QueensStepper.next(small, QueensBoard(), true, null, smallSolution), step)
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
        val play = Play(small)
        play.place(Cell(3))
        play.place(Cell(12))

        assertEquals(QueensStep.Mistake(Cell(12), QueensStep.Mistake.Kind.OFF_SOLUTION, null), play.next())

        play.session = play.session.withCrossToggled(Cell(2), QueensUiFixtures.T0)
        assertEquals(QueensStep.Mistake(Cell(3), QueensStep.Mistake.Kind.OFF_SOLUTION, null), play.next())
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

    // Erase, culprit, key, Spotlight

    @Test
    fun `every kind of mistake goes to the erase hook once, as given`() {
        val play = Play(small)
        val mistakes = listOf(
            QueensStep.Mistake(Cell(2), QueensStep.Mistake.Kind.OFF_SOLUTION, null),
            QueensStep.Mistake(Cell(5), QueensStep.Mistake.Kind.RULE_BREAK, Cell(1)),
            QueensStep.Mistake(Cell(1), QueensStep.Mistake.Kind.WRONG_CROSS, null),
        )

        mistakes.forEach(play.game::eraseCulprit)

        assertEquals(mistakes, play.erased)
    }

    @Test
    fun `only a mistake is a culprit, and only shown steps have keys`() {
        val game = Play(big).game
        val deduction = game.capture()()
        assertTrue("precondition: the empty board opens with a deduction, got $deduction", deduction is QueensStep.Deduction)
        val mistake = QueensStep.Mistake(Cell(2), QueensStep.Mistake.Kind.OFF_SOLUTION, null)
        val reveal = QueensStep.Reveal(Cell(2), holdsQueen = false)

        assertEquals(listOf(true, false, false), listOf(mistake, deduction, reveal).map(game::isCulprit))
        assertNull(game.keyOf(QueensStep.Solved))
        assertNull(game.keyOf(QueensStep.Unavailable))
        assertFalse(game.isCulprit(QueensStep.Solved))
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
