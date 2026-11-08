package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The spangram toggle and the pocket marks, through the editor's tags. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsEditorSpangramTest {

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
    fun `toggling marks the selected path as the spangram and again clears it`() {
        path(0, 1)
        controller.toggleSpangram(0)
        assertEquals(0, board().spangram)
        controller.toggleSpangram(0)
        assertEquals(StrandsDraft.NO_SPANGRAM, board().spangram)
    }

    @Test
    fun `marking another path moves the spangram`() {
        path(0, 1)
        path(6, 7)
        controller.toggleSpangram(0)
        controller.toggleSpangram(1)

        assertEquals(1, board().spangram)
    }

    @Test
    fun `a toggle is one undoable step and an index no path has does nothing`() {
        path(0, 1)
        val before = edits.size
        controller.toggleSpangram(5)
        assertEquals(before, edits.size)
        controller.toggleSpangram(0)
        assertEquals(before + 1, edits.size)
    }

    @Test
    fun `the spangram tag follows the selected path and its state`() {
        path(0, 1)
        controller.selectPath(0)
        show()
        rule.onNodeWithTag("strands.editor.spangram.0").assertIsNotSelected().performClick()
        rule.onNodeWithTag("strands.editor.spangram.0").assertIsSelected()
        assertEquals(0, board().spangram)
    }

    @Test
    fun `pocket tags mark exactly the cells of small uncovered areas`() {
        val covered = (0 until 48).filter { it !in setOf(0, 47) }
        val fresh = EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)
        val start = fresh.state.draft
        val walled = start.copy(paths = listOf(StrandsDraftPath(covered, null)))
        controller.adopt(fresh.apply(ReplaceDraft(start, walled)))
        show()
        rule.onNodeWithTag("strands.editor.cell.0.pocket").assertExists()
        rule.onNodeWithTag("strands.editor.cell.47.pocket").assertExists()
        rule.onNodeWithTag("strands.editor.cell.5.pocket").assertDoesNotExist()
    }
}
