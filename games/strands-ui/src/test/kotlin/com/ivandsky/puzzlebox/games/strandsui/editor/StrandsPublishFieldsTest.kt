package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPublishGates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Strands' publish details: the theme clue and the word list credit. Each keystroke sets the
 * clue, so the clue gate (1 to 40 characters) answers as the author types.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsPublishFieldsTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {
        edits += it
    }

    private fun board() = controller.draft.value.state.draft as StrandsDraft

    private fun clueBlocked() =
        StrandsEditor.publishBlocks(board(), Verdict.Checking).any { it.id == StrandsPublishGates.CLUE_LENGTH }

    private fun show(enabled: Boolean = true) = rule.setContent {
        PuzzleBoxTheme { StrandsEditorUi.PublishFields(controller, enabled, Modifier) }
    }

    @Test
    fun `the clue field and the credit line show`() {
        show()
        rule.onNodeWithTag(CLUE).assertExists()
        rule.onNodeWithTag(CREDIT).assertExists()
    }

    @Test
    fun `typing sets the clue keystroke by keystroke`() {
        show()
        rule.onNodeWithTag(CLUE).performTextInput("Kitchen")
        assertEquals("Kitchen", board().clue)
        assertTrue("every keystroke is an edit", edits.isNotEmpty())
        assertFalse(clueBlocked())
        rule.onNodeWithTag(COUNTER, useUnmergedTree = true).assertTextEquals("7/40")
    }

    @Test
    fun `an empty clue blocks publishing and says it is needed`() {
        show()
        assertTrue(clueBlocked())
        rule.onNode(hasTestTag(ERROR), useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(CLUE).assertExists()
        rule.onNode(hasTestTag(COUNTER), useUnmergedTree = true).assertTextEquals("0/40")
    }

    @Test
    fun `a 41 character clue blocks publishing and shows the cap's error`() {
        show()
        rule.onNodeWithTag(CLUE).performTextReplacement("A".repeat(41))
        assertEquals(41, board().clue.length)
        assertTrue(clueBlocked())
        rule.onNode(hasTestTag(ERROR), useUnmergedTree = true).assertTextEquals("Up to 40 characters")
        rule.onNode(hasTestTag(COUNTER), useUnmergedTree = true).assertTextEquals("41/40")
    }

    @Test
    fun `a 40 character clue publishes`() {
        show()
        rule.onNodeWithTag(CLUE).performTextReplacement("A".repeat(40))
        assertFalse(clueBlocked())
        rule.onNode(hasTestTag(ERROR), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an existing clue fills the field`() {
        controller.setClue("Around the house")
        show()
        rule.onNodeWithTag(CLUE).assert(hasText("Around the house"))
    }

    @Test
    fun `a running publish disables the clue`() {
        show(enabled = false)
        rule.onNodeWithTag(CLUE).assertIsNotEnabled()
    }

    private companion object {
        const val CLUE = "strands.editor.clue"
        const val CREDIT = "strands.editor.credit"
        const val COUNTER = "strands.editor.clue.counter"
        const val ERROR = "strands.editor.clue.error"
    }
}
