package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Trace, backtrack, select, remove and blocked cells, driven through the editor's tags. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsEditorTraceTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {
        edits += it
    }

    private fun board() = controller.draft.value.state.draft as StrandsDraft

    private fun show() = rule.setContent {
        PuzzleBoxTheme {
            Column {
                StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier.weight(1f))
                StrandsEditorUi.ToolHint(controller, Modifier.fillMaxWidth())
            }
        }
    }

    private fun tap(vararg cells: Int) = cells.forEach {
        rule.onNodeWithTag("strands.editor.cell.$it").performClick()
    }

    private fun trace(vararg cells: Int) {
        tap(*cells)
        tap(cells.last())
    }

    @Test
    fun `tapping adjacent cells then the tail again adds one path`() {
        show()
        trace(0, 1, 2)

        assertEquals(listOf(listOf(0, 1, 2)), board().paths.map { it.cells })
        assertEquals(1, edits.size)
        assertTrue(controller.trace.value.isEmpty())
    }

    @Test
    fun `tapping an earlier cell backtracks the trace`() {
        show()
        tap(0, 1, 2, 1)

        assertEquals(listOf(0, 1), controller.trace.value)
    }

    @Test
    fun `dragging back over the previous cell backtracks`() {
        controller.traceTo(0)
        controller.traceTo(1)
        controller.traceTo(2)
        controller.traceTo(1)

        assertEquals(listOf(0, 1), controller.trace.value)
    }

    @Test
    fun `a cell that is not adjacent restarts the trace there`() {
        show()
        tap(0, 3)

        assertEquals(listOf(3), controller.trace.value)
    }

    @Test
    fun `cells of another path cannot be traced over`() {
        show()
        trace(0, 1)
        tap(7)
        tap(1)

        assertEquals(listOf(7), controller.trace.value)
        assertEquals(1, board().paths.size)
        assertEquals(1, edits.size)
    }

    @Test
    fun `a drag cannot enter a cell another path holds`() {
        show()
        trace(0, 1)
        controller.traceTo(6)
        controller.traceTo(1)

        assertEquals(listOf(6), controller.trace.value)
    }

    @Test
    fun `tapping a traced cell selects its path without an edit`() {
        show()
        trace(0, 1)
        tap(1)

        assertEquals(0, controller.selectedPath.value)
        assertEquals(1, edits.size)
        rule.onNodeWithTag("strands.editor.remove").assertExists()
    }

    @Test
    fun `a lifted trace selects its path with a hint, no tap needed`() {
        show()
        trace(0, 1, 2)

        assertEquals(0, controller.selectedPath.value)
        rule.onNodeWithTag("strands.editor.hint").assertTextEquals("Unnamed · 3")
        rule.onNodeWithTag("strands.editor.remove").assertExists()
    }

    @Test
    fun `tapping a path chip selects it and a second tap lets it go`() {
        show()
        trace(0, 1)
        trace(6, 7)
        controller.selectPath(null)

        rule.onNodeWithTag("strands.editor.path.1").performScrollTo().performClick()
        assertEquals(1, controller.selectedPath.value)
        rule.onNodeWithTag("strands.editor.path.1").performScrollTo().performClick()
        assertNull(controller.selectedPath.value)
        rule.onNodeWithTag("strands.editor.remove").assertDoesNotExist()
    }

    @Test
    fun `removing the selected path is one undo step`() {
        show()
        trace(0, 1)
        trace(6, 7)
        rule.onNodeWithTag("strands.editor.path.0").performClick()
        rule.onNodeWithTag("strands.editor.remove").performClick()

        assertEquals(listOf(listOf(6, 7)), board().paths.map { it.cells })
        assertNull(controller.selectedPath.value)
        assertEquals(3, edits.size)
        val undone = controller.draft.value.undo()
        assertNotNull(undone)
        assertEquals(2, (undone!!.state.draft as StrandsDraft).paths.size)
    }

    @Test
    fun `starting a trace lets the selection go`() {
        show()
        trace(0, 1)
        tap(0)
        tap(12)

        assertNull(controller.selectedPath.value)
    }

    @Test
    fun `the hint is there with nothing selected and with a path selected`() {
        show()
        rule.onNodeWithTag("strands.editor.hint").assertExists()
        trace(0, 1)
        tap(0)
        rule.onNodeWithTag("strands.editor.hint").assertExists()
    }

    @Test
    fun `an undo that drops the selected path clears the selection`() {
        controller.traceTo(0)
        controller.traceTo(1)
        controller.endTrace()
        controller.selectPath(0)
        controller.adopt(controller.draft.value.undo()!!)

        assertNull(controller.selectedPath.value)
    }
}
