package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayFixture
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what the Tango game adds to [StepHints]: the solution searched once per puzzle (A19), `lastMove`, the mapper
 * wiring, the erase hook, and that a press places nothing (A1). Driven through a real [StepHints] on
 * [Dispatchers.Unconfined] (or a held compute dispatcher), so a press delivers at once.
 */
class TangoStepGameTest {

    private val puzzle: TangoPuzzle = TangoPlayFixture.puzzle
    private val solution: List<TangoCell> = TangoPlayFixture.solution

    /** A compute dispatcher that holds every block until [release]. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()
        val isHolding: Boolean get() = held.isNotEmpty()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            held += block
        }

        fun release() {
            while (held.isNotEmpty()) held.removeFirst().run()
        }
    }

    /** One play: the live session, the game under test, and the real press flow around it. */
    private inner class Play(
        board: TangoPuzzle = puzzle,
        solve: ((TangoPuzzle) -> List<TangoCell>?)? = null,
        compute: CoroutineDispatcher = Dispatchers.Unconfined,
    ) {
        var session = TangoSession(ref = null, puzzle = board)
        val erased = mutableListOf<TangoStep.Mistake>()
        var used = 0
        val game = if (solve == null) {
            TangoStepGame(session = { session }, onEraseCulprit = { erased += it })
        } else {
            TangoStepGame(session = { session }, onEraseCulprit = { erased += it }, solve = solve)
        }
        val hints = StepHints(HintMode.Stepwise(compute), CoroutineScope(Dispatchers.Unconfined), game, { used }, { used = it })

        /** What the next press would find, read off the game's own pure block. */
        fun next(): TangoStep = game.capture()()

        /** Presses, checks the Spotlight on screen is exactly the step's, and returns that step. */
        fun press(): TangoStep {
            val step = next()
            hints.press()
            val shown = hints.spotlight
            assertEquals(TangoSpotlightMapper.map(step, session.puzzle, session.grid(), shown?.playId ?: 0), shown)
            return step
        }

        /** A player's edit, then the input notice the controller gives (31). */
        fun place(idx: Int, value: TangoCell?) {
            session = session.withMove(TangoMove(idx, value), Instant.EPOCH)
            hints.inputMade()
        }

        fun clear() {
            session = session.withCleared()
            hints.inputMade()
        }
    }

    private fun opposite(value: TangoCell) = if (value == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN

    private val emptyCells: List<Int> get() = (0 until puzzle.cellCount).filter { puzzle.givens[it] == null }

    // The solution, once per puzzle (A19)

    @Test
    fun `the solution is searched once per puzzle, however many presses and inputs`() {
        var searches = 0
        val play = Play(solve = { searches++; solution })

        play.press()
        play.press()
        play.place(emptyCells[0], solution[emptyCells[0]])
        play.press()
        play.clear()
        play.press()

        assertEquals(1, searches)
    }

    @Test
    fun `a second game for the same puzzle searches again`() {
        var searches = 0
        val solve: (TangoPuzzle) -> List<TangoCell>? = { searches++; solution }

        Play(solve = solve).press()
        Play(solve = solve).press()

        assertEquals(2, searches)
    }

    @Test
    fun `the default search finds the published solution`() {
        assertEquals(solution, TangoStepGame.boundedSolution(puzzle))
    }

    @Test
    fun `an ambiguous board has no solution`() {
        assertNull(TangoStepGame.boundedSolution(TangoPlayFixture.ambiguous))
    }

    /** The fixture settles at the search's first node (propagation fills it): one poll is enough, zero is over budget. */
    @Test
    fun `the budget counts search nodes, and an over-budget search gives no solution the same way every run`() {
        assertEquals(solution, TangoStepGame.boundedSolution(puzzle, pollLimit = 1))
        repeat(3) { assertNull(TangoStepGame.boundedSolution(puzzle, pollLimit = 0)) }
    }

    @Test
    fun `without a solution a merely off-solution entry is not accused and nothing is revealed`() {
        val overBudget: (TangoPuzzle) -> List<TangoCell>? = { TangoStepGame.boundedSolution(it, pollLimit = 0) }
        val play = Play(solve = overBudget)
        val cell = offSolutionCellBreakingNoRule()
        play.place(cell, opposite(solution[cell]))

        val step = play.press()

        assertTrue("$step", step !is TangoStep.Mistake && step !is TangoStep.Reveal)
        assertEquals(TangoStepper.next(puzzle, play.session.grid(), cell, null), step)
        assertEquals(step, Play(solve = overBudget).apply { place(cell, opposite(solution[cell])) }.press())
    }

    @Test
    fun `with the solution the same entry is the culprit`() {
        val play = Play()
        val cell = offSolutionCellBreakingNoRule()
        play.place(cell, opposite(solution[cell]))

        assertEquals(cell, (play.press() as TangoStep.Mistake).idx)
    }

    @Test
    fun `the search runs inside the compute block, not at capture`() {
        var searches = 0
        val held = HeldDispatcher()
        val play = Play(solve = { searches++; solution }, compute = held)

        play.hints.press()

        assertEquals(0, searches)
        assertTrue(held.isHolding)
        assertNull(play.hints.spotlight)

        held.release()

        assertEquals(1, searches)
        assertNotNull(play.hints.spotlight)
    }

    // A press places nothing (A1)

    @Test
    fun `a press leaves the session untouched`() {
        val play = Play()
        val before = play.session

        play.press()
        play.press()

        assertEquals(before, play.session)
    }

    // Wrong sun or moon

    @Test
    fun `a wrong entry is shown once as the culprit, then the second press hands it to the erase hook`() {
        val play = Play()
        val cell = emptyCells[0]
        play.place(cell, opposite(solution[cell]))
        val wrong = play.session

        val step = play.press()

        assertEquals(cell, (step as TangoStep.Mistake).idx)
        assertEquals(listOf(cell), play.hints.spotlight?.roles?.filterValues { it == SpotlightRole.CULPRIT }?.keys?.toList())
        assertTrue(play.erased.isEmpty())

        play.hints.press()

        assertEquals(listOf(step), play.erased)
        assertNull(play.hints.spotlight)
        assertEquals(wrong, play.session)
    }

    // lastMove

    @Test
    fun `of two equal deductions the one nearer the newest placement is shown`() {
        val (cell, grid) = emptyCells.asSequence()
            .map { cell -> cell to List(puzzle.cellCount) { puzzle.givens[it] ?: solution[it].takeIf { _ -> it == cell } } }
            .first { (cell, grid) -> TangoStepper.next(puzzle, grid, cell, solution) != TangoStepper.next(puzzle, grid, null, solution) }
        val play = Play()
        play.place(cell, solution[cell])
        assertEquals(grid, play.session.grid())

        assertEquals(TangoStepper.next(puzzle, grid, cell, solution), play.press())
    }

    @Test
    fun `after a clear there is no last move and the step is deterministic`() {
        val play = Play()
        play.place(emptyCells[0], solution[emptyCells[0]])
        play.clear()

        val step = play.press()

        assertEquals(TangoStepper.next(puzzle, play.session.grid(), null, solution), step)
        assertEquals(step, Play().next())
    }

    // Solved, Unavailable and the mapper

    @Test
    fun `solved and unavailable steps have no key`() {
        val game = Play().game
        assertNull(game.keyOf(TangoStep.Solved))
        assertNull(game.keyOf(TangoStep.Unavailable))
    }

    @Test
    fun `an unavailable step shows nothing and counts nothing`() {
        val play = Play(board = TangoPlayFixture.ambiguous)

        assertEquals(TangoStep.Unavailable, play.press())

        assertNull(play.hints.spotlight)
        assertEquals(0, play.used)
    }

    @Test
    fun `a completed session shows nothing and counts nothing`() {
        val play = Play()
        TangoPlayFixture.placements.forEach { (idx, value) -> play.place(idx, value) }
        assertTrue(play.session.isComplete)

        assertEquals(TangoStep.Solved, play.press())

        assertNull(play.hints.spotlight)
        assertEquals(0, play.used)
    }

    @Test
    fun `the spotlight is the mapper's for the live board, the wrong entry's glyph included`() {
        val play = Play()
        val cell = emptyCells[0]
        play.place(cell, opposite(solution[cell]))
        val step = play.next()

        val spotlight = play.game.spotlight(step, 7)

        assertEquals(TangoSpotlightMapper.map(step, puzzle, play.session.grid(), 7), spotlight)
        assertEquals(TangoSpotlightGlyphs(puzzle.n, mapOf(cell to opposite(solution[cell]))), spotlight?.glyphs)
    }

    /** A player cell whose wrong value breaks no rule on the bare board, so only the solution can accuse it. */
    private fun offSolutionCellBreakingNoRule(): Int = emptyCells.first { cell ->
        val grid = List(puzzle.cellCount) { puzzle.givens[it] ?: opposite(solution[it]).takeIf { _ -> it == cell } }
        TangoStepper.next(puzzle, grid, cell, null) !is TangoStep.Mistake
    }
}
