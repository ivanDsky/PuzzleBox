package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
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
 * Pins the Stepwise wiring the board state owns (ticket 34): a press shows a Spotlight and places nothing (A1), counts
 * once through the session (A9), marks a wrong digit and re-shows the mark for free without touching the board (A4),
 * clears on every input (A10), hides the caption and the violation line while it shows (A11), and the grid draws it
 * without taking taps. The press rules themselves are StepHints' (04b) and the game's `known` is SudokuStepGame's (33).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuStepwiseHintTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private val peanut450 = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
        grid("463251 521436 142563 356142 234615 615324"),
    )

    /** Lab board rnd:154: no single, so the first press shows an elimination. */
    private val noSingle = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..62.4 42.6.. 6.142. 24...6 .6254. ..4.62"),
        grid("516234 423651 631425 245316 162543 354162"),
    )

    private var settled = 0

    private fun stepwise(
        session: SudokuSession = SudokuSession(ref = null, puzzle = peanut450),
        compute: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = SudokuBoardState(
        initialSession = session,
        now = { Instant.EPOCH },
        settle = { settled++; it },
        hintMode = HintMode.Stepwise(compute),
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    /** Cell 0 holds a wrong 1 that also clashes with the given 1 at cell 12. */
    private fun withWrongOne(): SudokuBoardState = stepwise().apply {
        select(0)
        input(WRONG_DIGIT)
    }

    private fun SudokuBoardState.culprits(): Set<Int> =
        spotlight?.roles?.filterValues { it == SpotlightRole.CULPRIT }?.keys.orEmpty()

    // Press (A1, A9, A15)

    @Test
    fun `a press shows a spotlight, places nothing, draws no legacy wash and counts once`() {
        val s = stepwise()
        val before = s.session.board

        s.requestHint()

        assertNotNull(s.spotlight)
        assertEquals(before, s.session.board)
        assertNull(s.hint)
        assertNull(s.hintTarget)
        assertTrue(s.hintCells.isEmpty())
        assertTrue(s.hintStruck.isEmpty())
        assertTrue(s.model.cells.none { it.hintTarget || it.hintUnit || it.hintStruck })
        assertEquals(1, s.session.hintsUsed)
        assertEquals(1, settled)
    }

    @Test
    fun `re-pressing is free and never fills the cell in`() {
        val s = stepwise()
        s.requestHint()
        val board = s.session.board

        s.requestHint()
        s.requestHint()

        assertNotNull(s.spotlight)
        assertEquals(board, s.session.board)
        assertFalse(s.canUndo)
        assertEquals(1, s.session.hintsUsed)
    }

    // Wrong digit: mark only (A4)

    @Test
    fun `the second press on a wrong digit re-shows the mark for free and changes nothing`() {
        val s = withWrongOne()

        s.requestHint()
        assertEquals(setOf(0), s.culprits())
        assertEquals(1, s.session.hintsUsed)
        val marked = s.session
        val playId = checkNotNull(s.spotlight).playId

        s.requestHint()
        assertEquals(setOf(0), s.culprits())
        assertEquals(playId + 1, s.spotlight?.playId)
        assertEquals(WRONG_DIGIT, s.session.board.digitAt(0))
        assertEquals(marked.board, s.session.board)
        assertEquals(marked.history, s.session.history)
        assertEquals(1, s.session.hintsUsed)
    }

    @Test
    fun `the player's own erase takes the marked digit off and clears the mark`() {
        val s = withWrongOne()
        s.requestHint()
        s.requestHint()

        s.select(0)
        s.erase()

        assertEquals(SudokuCell.Empty, s.session.board.cells[0])
        assertNull(s.spotlight)
        assertEquals(1, s.session.hintsUsed)
    }

    // Caption (A11)

    @Test
    fun `while the spotlight shows the caption and the violation are hidden, then both return`() {
        val s = withWrongOne()
        val violation = checkNotNull(s.violation)

        s.requestHint()
        assertNotNull(s.spotlight)
        assertNull(s.hintCaption)
        assertNull(s.violation)

        s.select(20)
        assertNull(s.spotlight)
        assertEquals(violation, s.violation)
        assertNull(s.hintCaption)
    }

    // Input clears (A10)

    @Test
    fun `every board input clears the spotlight in the same call`() {
        val inputs: List<Pair<String, (SudokuBoardState) -> Unit>> = listOf(
            "select" to { s: SudokuBoardState -> s.select(20) },
            "input" to { s: SudokuBoardState -> s.input(4) },
            "erase" to { s: SudokuBoardState -> s.erase() },
            "clear" to { s: SudokuBoardState -> s.clear() },
            "undo" to { s: SudokuBoardState -> s.undo() },
            "redo" to { s: SudokuBoardState -> s.redo() },
            "toggleNotesMode" to { s: SudokuBoardState -> s.toggleNotesMode() },
        )
        for ((name, action) in inputs) {
            val s = stepwise()
            s.select(0)
            s.input(4)
            s.undo()
            s.requestHint()
            assertNotNull(name, s.spotlight)

            action(s)

            assertNull(name, s.spotlight)
        }
    }

    @Test
    fun `a shown elimination is not shown again after an input`() {
        val s = stepwise(SudokuSession(ref = null, puzzle = noSingle))
        s.requestHint()
        val first = checkNotNull(s.spotlight)
        assertTrue(SpotlightRole.EXCLUDED in first.roles.values)

        s.toggleNotesMode()
        s.requestHint()

        val second = checkNotNull(s.spotlight)
        assertNotEquals(first.roles, second.roles)
        assertEquals(2, s.session.hintsUsed)
    }

    @Test
    fun `a press still computing when an input lands never shows`() {
        val scheduler = TestCoroutineScheduler()
        val s = stepwise(compute = StandardTestDispatcher(scheduler))
        s.select(0)

        s.requestHint()
        s.input(4)
        scheduler.advanceUntilIdle()

        assertNull(s.spotlight)
        assertEquals(0, s.session.hintsUsed)

        s.requestHint()
        scheduler.advanceUntilIdle()
        assertNotNull(s.spotlight)
    }

    // Count and completion (A9)

    @Test
    fun `at the ceiling a new press keeps 255 and the session still serialises`() {
        val s = stepwise(SudokuSession(ref = null, puzzle = peanut450, hintsUsed = 255))

        s.requestHint()

        assertNotNull(s.spotlight)
        assertEquals(255, s.session.hintsUsed)
        val decoded = SudokuSessionCodec.deserialize(SudokuSessionCodec.serialize(s.session))
        assertEquals(255, (decoded as DecodeResult.Ok).value.hintsUsed)
    }

    @Test
    fun `a completed session shows nothing and counts nothing`() {
        var solved = SudokuSession(ref = null, puzzle = peanut450)
        peanut450.solution.forEachIndexed { cell, digit ->
            SudokuMoves.place(solved.board, cell, digit)?.let { solved = solved.withCommand(it, Instant.EPOCH) }
        }
        assertTrue(solved.isComplete)
        val s = stepwise(solved)

        s.requestHint()

        assertNull(s.spotlight)
        assertEquals(solved, s.session)
    }

    // Overlay

    private fun show(state: SudokuBoardState) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SudokuGrid(state.model, state::select, Modifier.size(360.dp), spotlight = state.spotlight)
            }
        }
        composeRule.waitForIdle()
    }

    private fun cell(idx: Int) = composeRule.onNodeWithTag("sudoku.cell.$idx")

    private fun calloutShown(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the callout shows on the grid and a tap on a lit cell selects it and clears it`() {
        val s = stepwise()
        show(s)
        s.requestHint()
        composeRule.waitForIdle()
        val spotlight = checkNotNull(s.spotlight)
        val text = checkNotNull(spotlight.callout).text
        assertTrue(calloutShown(text))
        val lit = spotlight.roles.keys.first { spotlight.roles[it] != SpotlightRole.EXCLUDED }

        cell(lit).performClick()
        composeRule.waitForIdle()

        assertEquals(lit, s.selected)
        assertNull(s.spotlight)
        assertFalse(calloutShown(text))
    }

    @Test
    fun `a tap on a dimmed cell still selects it`() {
        val s = stepwise()
        show(s)
        s.requestHint()
        composeRule.waitForIdle()
        val dimmed = (0 until 36).first { it !in checkNotNull(s.spotlight).roles }

        cell(dimmed).performClick()
        composeRule.waitForIdle()

        assertEquals(dimmed, s.selected)
        assertNull(s.spotlight)
    }

    /** The unmerged tree as text, minus node ids and identity hashes (CollectionItemInfo has no equals). */
    private fun semanticsTree(): String =
        composeRule.onRoot(useUnmergedTree = true).printToString().replace(Regex("#\\d+|@[0-9a-f]+"), "")

    @Test
    fun `once the spotlight clears the semantics tree is the one before the press`() {
        val s = stepwise()
        show(s)
        val before = semanticsTree()

        s.requestHint()
        composeRule.waitForIdle()
        assertNotEquals(before, semanticsTree())
        s.undo()
        composeRule.waitForIdle()

        assertEquals(before, semanticsTree())
    }

    /** Red: the board's violation digit and the culprit glyph, not their anti-aliased fringe. */
    private fun redPixels(): Set<Int> {
        val pixels = cell(0).captureToImage().toPixelMap()
        return (0 until pixels.width * pixels.height).filterTo(HashSet()) {
            val c = pixels[it % pixels.width, it / pixels.width]
            c.red > RED_MIN && c.green < OTHER_MAX && c.blue < OTHER_MAX
        }
    }

    @Test
    fun `the culprit glyph covers every red pixel of the board's own wrong digit`() {
        val s = withWrongOne()
        s.select(20)
        show(s)
        val board = redPixels()
        assertTrue("the wrong digit is red without a Spotlight", board.isNotEmpty())

        s.requestHint()
        composeRule.waitForIdle()
        assertEquals(setOf(0), s.culprits())
        val covered = redPixels()

        assertEquals("board pixels not red under the culprit", emptySet<Int>(), board - covered)
    }

    private companion object {
        /** Not cell 0's solution (4), and a clash with the given 1 in column 1. */
        const val WRONG_DIGIT = 1
        const val RED_MIN = 0.5f
        const val OTHER_MAX = 0.35f
    }
}
