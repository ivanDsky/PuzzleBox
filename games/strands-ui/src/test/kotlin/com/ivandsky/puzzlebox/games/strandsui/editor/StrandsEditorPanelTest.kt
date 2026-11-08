package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The paths panel, the clue field and the credit line, through the editor's tags. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsEditorPanelTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {
        edits += it
    }

    private fun board() = controller.draft.value.state.draft as StrandsDraft

    private fun show() = rule.setContent {
        PuzzleBoxTheme { Column { StrandsEditorUi.Board(controller, Verdict.Checking, Modifier.weight(1f)) } }
    }

    private fun path(vararg cells: Int) {
        cells.forEach(controller::traceTo)
        controller.endTrace()
    }

    @Test
    fun `one row per path and none beyond the last`() {
        path(0, 1)
        path(6, 7, 8)
        show()
        rule.onNodeWithTag("strands.editor.path.0").assertExists()
        rule.onNodeWithTag("strands.editor.path.1").assertExists()
        rule.onNodeWithTag("strands.editor.path.2").assertDoesNotExist()
    }

    @Test
    fun `only the spangram row carries the mark`() {
        path(0, 1)
        path(6, 7)
        controller.toggleSpangram(1)
        show()
        rule.onNode(hasTestTag("strands.editor.path.1.spangram"), useUnmergedTree = true).assertExists()
        rule.onNode(hasTestTag("strands.editor.path.0.spangram"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a tap selects the row and a second tap lets it go`() {
        path(0, 1)
        path(6, 7)
        show()
        rule.onNodeWithTag("strands.editor.path.1").performScrollTo().assertIsNotSelected().performClick()
        rule.onNodeWithTag("strands.editor.path.1").assertIsSelected()
        assertEquals(1, controller.selectedPath.value)
        rule.onNodeWithTag("strands.editor.path.1").performScrollTo().performClick()
        assertNull(controller.selectedPath.value)
    }

    @Test
    fun `a committed clue is one undoable step`() {
        show()
        rule.onNodeWithTag("strands.editor.clue").performTextInput("Kitchen")
        assertEquals(0, edits.size)
        rule.onNodeWithTag("strands.editor.clue").performImeAction()

        assertEquals("Kitchen", board().clue)
        assertEquals(1, edits.size)
        controller.adopt(checkNotNull(controller.draft.value.undo()))
        assertEquals("", board().clue)
    }

    @Test
    fun `a clue commits when focus leaves the field`() {
        show()
        rule.onNodeWithTag("strands.editor.clue").performClick()
        rule.onNodeWithTag("strands.editor.clue").performTextInput("Kitchen")
        assertEquals(0, edits.size)
        rule.onNodeWithTag("strands.editor.word").performClick()

        assertEquals("Kitchen", board().clue)
        assertEquals(1, edits.size)
        controller.adopt(checkNotNull(controller.draft.value.undo()))
        assertEquals("", board().clue)
    }

    @Test
    fun `committing an unchanged clue makes no step`() {
        controller.setClue("")
        controller.setClue("Kitchen")
        controller.setClue("Kitchen")
        assertEquals(1, edits.size)
    }

    @Test
    fun `the credit line is in the panel`() {
        show()
        rule.onNodeWithTag("strands.editor.credit").assertExists()
    }
}
