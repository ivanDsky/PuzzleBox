package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The paths panel through the editor's tags. */
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
        PuzzleBoxTheme { Column { StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier.weight(1f)) } }
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
    fun `committing an unchanged clue makes no step`() {
        controller.setClue("")
        controller.setClue("Kitchen")
        controller.setClue("Kitchen")
        assertEquals(1, edits.size)
    }
}
