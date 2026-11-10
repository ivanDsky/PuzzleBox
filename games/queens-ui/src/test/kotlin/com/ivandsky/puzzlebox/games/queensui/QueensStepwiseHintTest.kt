package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queensui.hint.QueensStepGame
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the Stepwise wiring through the controller (ticket 28): a press shows a Spotlight and never reaches the legacy
 * region pulse or crown ghost (A1, A15), counts once into the saved session (A9), marks a wrong queen or cross and
 * re-shows it for free without touching the board (A4), clears on every board input (A10), hides the rule-conflict
 * line while it shows (A11), and the board draws it over the cell grid without taking taps. The press rules
 * themselves are StepHints' (04b), the step is QueensStepGame's (27).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class QueensStepwiseHintTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val saved = ArrayList<ByteArray>()

    private fun stepwise(
        compute: CoroutineDispatcher = Dispatchers.Unconfined,
        puzzle: QueensPuzzle = QueensPlayFixtures.puzzle(),
    ) = QueensPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, {}),
        settings = InMemoryQueensSettings(),
        now = { Instant.EPOCH },
        hintMode = HintMode.Stepwise(compute),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(QueensPlayFixtures.envelope(puzzle), QueensPlayFixtures.ref())) }

    private val QueensPlayController.state: QueensBoardState get() = checkNotNull(board)

    private fun savedSession(): QueensSession = (QueensSessionCodec.deserialize(saved.last()) as DecodeResult.Ok).value

    private fun QueensBoardState.culprits(): Set<Int> =
        spotlight?.roles?.filterValues { it == SpotlightRole.CULPRIT }?.keys.orEmpty()

    /** Taps [idx] until it holds a queen (the tap cycle: empty → X → queen). */
    private fun QueensBoardState.placeQueen(idx: Int) {
        while (Cell(idx) !in session.queens) onTap(idx)
    }

    // Press (A1, A9, A15)

    @Test
    fun `a press shows a spotlight, never the legacy hint, and counts once into the saved session`() {
        val c = stepwise()
        val board = c.state.session.board

        c.requestHint()

        assertNotNull(c.state.spotlight)
        assertNull(c.state.hint)
        assertFalse(c.state.hintPointed)
        assertEquals(board, c.state.session.board)
        assertFalse(c.canUndo)
        assertEquals(1, c.hintsUsed)
        assertEquals(1, savedSession().hintsUsed)
    }

    @Test
    fun `a deduction that crosses cells out crosses nothing on the board and counts once`() {
        val c = stepwise()
        c.state.setAutoCross(false)
        c.state.placeQueen(SOLUTION_CELL) // auto-cross off: the queen's attacks stay open
        val before = c.state.session

        c.requestHint()

        val roles = checkNotNull(c.state.spotlight).roles
        assertTrue("$roles", SpotlightRole.EVIDENCE in roles.values) // a deduction, never a reveal
        assertTrue("$roles", SpotlightRole.EXCLUDED in roles.values) // it crosses cells out
        assertEquals(before.board, c.state.session.board)
        assertEquals(before.marks, c.state.session.marks)
        assertEquals(before.history, c.state.session.history)
        assertEquals(1, c.hintsUsed)
        assertEquals(1, savedSession().hintsUsed)
    }

    @Test
    fun `re-pressing the same step is free and never escalates to the crown ghost`() {
        val c = stepwise()
        c.requestHint()

        c.requestHint()
        c.requestHint()

        assertNotNull(c.state.spotlight)
        assertNull(c.state.hint)
        assertFalse(c.state.hintPointed)
        assertEquals(1, c.hintsUsed)
    }

    @Test
    fun `after an unrelated move the same step re-shows twice for free`() {
        val c = stepwise()
        c.requestHint()
        val first = checkNotNull(c.state.spotlight)
        assertEquals(1, c.hintsUsed)

        c.state.onTap(UNRELATED_CROSS) // a right × off the step
        assertNull(c.state.spotlight)
        c.requestHint()
        c.requestHint()

        assertEquals(first.roles, c.state.spotlight?.roles)
        assertEquals(1, c.hintsUsed)
        assertEquals(1, savedSession().hintsUsed)
    }

    // Culprit mark only (A4)

    @Test
    fun `the second press on a wrong queen re-shows the mark for free and changes nothing`() {
        val c = stepwise()
        c.state.placeQueen(WRONG_QUEEN)

        c.requestHint()
        assertEquals(setOf(WRONG_QUEEN), c.state.culprits())
        assertEquals(1, c.hintsUsed)
        val marked = c.state.session
        val playId = checkNotNull(c.state.spotlight).playId

        c.requestHint()
        assertEquals(setOf(WRONG_QUEEN), c.state.culprits())
        assertEquals(playId + 1, c.state.spotlight?.playId)
        assertEquals(marked.board, c.state.session.board)
        assertEquals(marked.history, c.state.session.history)
        assertEquals(1, c.hintsUsed)
        assertEquals(1, savedSession().hintsUsed)
    }

    @Test
    fun `the second press on a wrong cross re-shows the mark and leaves the cross and undo alone`() {
        val c = stepwise()
        c.state.onTap(SOLUTION_CELL) // a manual × on a solution cell
        val crossed = c.state.session

        c.requestHint()
        assertEquals(setOf(SOLUTION_CELL), c.state.culprits())
        c.requestHint()
        assertEquals(setOf(SOLUTION_CELL), c.state.culprits())
        assertEquals(MarkKind.MANUAL, c.state.session.markAt(Cell(SOLUTION_CELL))?.kind)
        assertEquals(crossed.history, c.state.session.history)
        assertEquals(1, c.hintsUsed)
    }

    @Test
    fun `the player's own tap takes the marked queen off and clears the mark`() {
        val c = stepwise()
        c.state.placeQueen(WRONG_QUEEN)
        c.requestHint()
        c.requestHint()

        c.state.onTap(WRONG_QUEEN)

        assertFalse(Cell(WRONG_QUEEN) in c.state.session.queens)
        assertNull(c.state.spotlight)
        assertEquals(1, c.hintsUsed)
    }

    // Caption (A11)

    @Test
    fun `while the spotlight shows the caption and the conflict line are hidden, then the line returns`() {
        val c = stepwise()
        c.state.placeQueen(WRONG_QUEEN)
        c.state.placeQueen(WRONG_QUEEN + 1) // same row: a clash
        val violation = checkNotNull(c.violation)

        c.requestHint()
        assertNotNull(c.state.spotlight)
        assertNull(c.hintCaption)
        assertNull(c.violation)

        c.state.onTap(FREE_CELL)
        assertNull(c.state.spotlight)
        assertEquals(violation, c.violation)
        assertNull(c.hintCaption)
    }

    // Input clears (A10)

    @Test
    fun `every board input clears the spotlight in the same call`() {
        val inputs: List<Pair<String, (QueensPlayController) -> Unit>> = listOf(
            "tap" to { c -> c.state.onTap(FREE_CELL) },
            "drag" to { c -> c.state.onDragCell(FREE_CELL) },
            "undo" to { c -> c.undo() },
            "redo" to { c -> c.redo() },
            "auto-cross" to { c -> c.state.setAutoCross(!c.state.autoCross) },
            "clear" to { c -> c.state.clearBoard() },
        )
        for ((name, input) in inputs) {
            val c = stepwise()
            c.state.onTap(FREE_CELL)
            c.undo() // something to redo
            c.requestHint()
            assertNotNull(name, c.state.spotlight)

            input(c)

            assertNull(name, c.state.spotlight)
        }
    }

    @Test
    fun `a tap outside the board changes nothing and keeps the spotlight`() {
        val c = stepwise()
        c.requestHint()

        c.state.onTap(QueensPlayFixtures.puzzle().cellCount)

        assertNotNull(c.state.spotlight)
    }

    @Test
    fun `a drag over an existing mark clears the spotlight and leaves the mark`() {
        val c = stepwise()
        c.state.onTap(FREE_CELL)
        c.requestHint()
        assertNotNull(c.state.spotlight)
        val marked = c.state.session

        c.state.onDragCell(FREE_CELL)

        assertNull(c.state.spotlight)
        assertEquals(marked, c.state.session)
    }

    @Test
    fun `a drag outside the board changes nothing and keeps the spotlight`() {
        val c = stepwise()
        c.requestHint()

        c.state.onDragCell(QueensPlayFixtures.puzzle().cellCount)

        assertNotNull(c.state.spotlight)
    }

    @Test
    fun `a press still computing when a tap lands never shows and counts nothing`() {
        val scheduler = TestCoroutineScheduler()
        val c = stepwise(compute = StandardTestDispatcher(scheduler))

        c.requestHint()
        c.state.onTap(FREE_CELL)
        scheduler.advanceUntilIdle()

        assertNull(c.state.spotlight)
        assertEquals(0, c.hintsUsed)
    }

    @Test
    fun `a press still computing at replay neither shows on the new board nor saves the old session`() {
        val scheduler = TestCoroutineScheduler()
        val c = stepwise(compute = StandardTestDispatcher(scheduler))
        c.state.onTap(FREE_CELL)
        c.requestHint()

        c.replay()
        val fresh = c.state.session
        scheduler.advanceUntilIdle()

        assertNull(c.state.spotlight)
        assertEquals(fresh, savedSession())
        assertEquals(0, c.hintsUsed)
    }

    // Completion

    /** Won with a valid layout that is not the stored solution: the step is Solved, not a mistake against the store. */
    @Test
    fun `a board won off the stored solution offers no hint and a press shows nothing`() {
        val c = stepwise(puzzle = QueensPlayFixtures.rowsPuzzle())
        OTHER_SOLUTION.forEach { c.state.placeQueen(it) }
        assertTrue(c.isComplete)
        val solved = c.state.session

        assertFalse(c.hintEnabled)
        c.requestHint()

        assertNull(c.state.spotlight)
        assertEquals(solved, c.state.session)
    }

    // Mode (A20)

    @Test
    fun `the play UI builds Stepwise controllers - a hint opens the spotlight`() {
        val ui = QueensPlayUi(InMemoryQueensSettings(), HintMode.Stepwise(Dispatchers.Unconfined))
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as QueensPlayController
        assertEquals(Adopt.Adopted, controller.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()))

        controller.requestHint()
        assertNotNull(controller.state.spotlight)
        assertNull(controller.state.hint)
    }

    // Overlay

    private fun show(c: QueensPlayController) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                QueensPlayBoard(c.state, Modifier.fillMaxWidth().testTag(BOARD_TAG))
            }
        }
        composeRule.waitForIdle()
    }

    private fun calloutShown(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the callout shows on the board, over exactly its cell grid, and a tap clears it`() {
        val c = stepwise()
        show(c)
        c.requestHint()
        composeRule.waitForIdle()
        val text = checkNotNull(c.state.spotlight?.callout).text
        assertTrue(calloutShown(text))
        val board = composeRule.onNodeWithTag(BOARD_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(board, composeRule.onNodeWithTag(SPOTLIGHT_TAG).fetchSemanticsNode().boundsInRoot)

        val n = QueensPlayFixtures.puzzle().size
        composeRule.onNodeWithTag(BOARD_TAG).performTouchInput {
            click(Offset(width * (FREE_CELL % n + 0.5f) / n, height * (FREE_CELL / n + 0.5f) / n))
        }
        composeRule.waitForIdle()

        assertEquals(MarkKind.MANUAL, c.state.session.markAt(Cell(FREE_CELL))?.kind)
        assertNull(c.state.spotlight)
        assertFalse(calloutShown(text))
    }

    // Fixtures (ticket 43: a stored solution is judged against only when it is the board's one solution)

    @Test
    fun `the play fixture has one layout and the rows board has another`() {
        val fixture = QueensPlayFixtures.puzzle()
        assertEquals(fixture.solution!!.toSet(), QueensStepGame.boundedSolution(fixture.copy(solution = null)))

        val rows = QueensPlayFixtures.rowsPuzzle()
        assertEquals(QueensRulesVerdict.Valid, QueensRules.evaluate(rows, OTHER_SOLUTION.map(::Cell).toSet()))
        assertNotEquals(rows.solution!!.toSet(), OTHER_SOLUTION.map(::Cell).toSet())
    }

    private companion object {
        /** Row 0, column 1: off the fixture's solution (column 0 in row 0). */
        const val WRONG_QUEEN = 1

        /** Row 0's solution cell. */
        const val SOLUTION_CELL = 0

        /** Row 7, column 7: free and off the solution, far from row 0. */
        const val FREE_CELL = 63

        /** Row 1, column 1: off the solution and off the first step, which stays the one shown after a × here. */
        const val UNRELATED_CROSS = 9

        /** Columns 1 3 5 7 2 0 6 4 by row: on [QueensPlayFixtures.rowsPuzzle] one queen per row (= region) and column, no two touching. */
        val OTHER_SOLUTION = listOf(1, 11, 21, 31, 34, 40, 54, 60)

        const val BOARD_TAG = "queens.test.board"
        const val SPOTLIGHT_TAG = "queens.spotlight"
    }
}
