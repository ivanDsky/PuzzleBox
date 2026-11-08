package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudokuui.digitKeyPlacedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuEditorInputTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val editor = SudokuEditor(SudokuGames.MINI)
    private val edits = ArrayList<EditorDraft<*>>()
    private val controller = SudokuEditorController(EditorDraft.fresh(editor, editor.variants.first())) {
        edits += it
    }

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme { SudokuEditorBoard(controller, SudokuGames.MINI.variant.geometry) }
        }
    }

    private fun givensOf(draft: EditorDraft<*>): List<Int> = (draft.state.draft as SudokuDraft).givens

    private fun assertKeyPlaced(digit: Int, placed: Int) {
        composeRule.onNodeWithTag("sudoku.editor.key.$digit").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, digitKeyPlacedState(placed)),
        )
    }

    @Test
    fun `keys count placed digits and every digit has its own tag`() {
        show()
        (1..6).forEach { assertKeyPlaced(it, 0) }

        composeRule.onNodeWithTag("sudoku.cell.0").performClick()
        composeRule.onNodeWithTag("sudoku.editor.key.4").performClick()
        composeRule.onNodeWithTag("sudoku.cell.7").performClick()
        composeRule.onNodeWithTag("sudoku.editor.key.4").performClick()

        assertKeyPlaced(4, 2)
        assertKeyPlaced(1, 0)
    }

    @Test
    fun `the keypad has no notes toggle`() {
        show()

        composeRule.onAllNodesWithTag("sudoku.notes_key").assertCountEquals(0)
        composeRule.onAllNodesWithTag("sudoku.clear").assertCountEquals(0)
    }

    @Test
    fun `erase empties the selected cell as its own step`() {
        show()
        composeRule.onNodeWithTag("sudoku.cell.2").performClick()
        composeRule.onNodeWithTag("sudoku.editor.key.3").performClick()
        assertKeyPlaced(3, 1)

        composeRule.onNodeWithTag("sudoku.editor.erase").performClick()

        assertKeyPlaced(3, 0)
        assertEquals(2, edits.size)
        assertEquals(0, givensOf(edits.last())[2])
    }

    @Test
    fun `erase on an empty selected cell is no edit`() {
        controller.tap(5)

        controller.erase()

        assertTrue(edits.isEmpty())
    }

    @Test
    fun `clear is off while the board holds no digit and erase stays on`() {
        show()

        composeRule.onNodeWithTag("sudoku.editor.clear").assertIsNotEnabled()
        composeRule.onNodeWithTag("sudoku.editor.erase").assertIsEnabled()
    }

    @Test
    fun `a digit or erase with nothing selected nudges and edits nothing`() {
        var nudges = 0
        val scope = TestScope(UnconfinedTestDispatcher())
        controller.nudge.onEach { nudges++ }.launchIn(scope)

        controller.digit(3)
        controller.erase()

        assertEquals(2, nudges)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `a digit with a cell selected does not nudge`() {
        var nudges = 0
        val scope = TestScope(UnconfinedTestDispatcher())
        controller.nudge.onEach { nudges++ }.launchIn(scope)
        controller.tap(1)

        controller.digit(3)
        controller.erase()

        assertEquals(0, nudges)
    }

    @Test
    fun `each entry is one undo step`() {
        controller.tap(0)
        controller.digit(1)
        controller.digit(2)
        controller.erase()

        assertEquals(3, edits.size)
        val last = edits.last()
        assertEquals(2, givensOf(last.undo()!!)[0])
        assertEquals(1, givensOf(last.undo()!!.undo()!!)[0])
        assertEquals(0, givensOf(last.undo()!!.undo()!!.undo()!!)[0])
    }

    @Test
    fun `clear empties the board as one step`() {
        controller.tap(0)
        controller.digit(1)
        controller.tap(3)
        controller.digit(2)

        controller.clear()

        assertEquals(3, edits.size)
        assertEquals(List(36) { 0 }, givensOf(edits.last()))
        assertEquals(2, givensOf(edits.last().undo()!!).count { it != 0 })
    }

    @Test
    fun `the tool hint shows in the tool strip and follows the selection`() {
        val ui = SudokuEditorUi(SudokuGames.MINI)
        composeRule.setContent { PuzzleBoxTheme { ui.ToolHint(controller, Modifier) } }
        val empty = composeRule.onNodeWithTag("sudoku.editor.hint").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)

        controller.tap(8)
        composeRule.waitForIdle()
        val picked = composeRule.onNodeWithTag("sudoku.editor.hint").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)

        assertTrue(empty != picked)
    }
}
