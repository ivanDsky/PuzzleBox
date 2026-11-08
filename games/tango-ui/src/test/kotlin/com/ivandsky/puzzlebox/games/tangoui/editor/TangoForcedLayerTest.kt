package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tango.editor.TangoForcedCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Forced cells toggle tags exactly the cells the hint ladder forces; a tap still cycles. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class TangoForcedLayerTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()
    private val start: EditorDraft<TangoDraft> = EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())
        .let { fresh ->
            EditorDraft(
                TangoEditor,
                fresh.state.copy(draft = fresh.state.draft.copy(givens = mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN))),
            )
        }
    private val controller = TangoEditorController(start) { edits += it }

    private fun board(): TangoDraft = controller.draft.value.state.draft as TangoDraft

    private fun forcedTags(): Set<String> =
        TangoForcedCells.of(board()).keys.map { "tango.editor.cell.$it.forced" }.toSet()

    private fun forcedCount() = rule.onAllNodesWithTag("tango.editor.forced", useUnmergedTree = true)

    private fun show() {
        rule.setContent { PuzzleBoxTheme { TangoEditorBoard(controller, Modifier.fillMaxWidth()) } }
    }

    private fun assertTagged(tags: Set<String>) {
        rule.waitUntil(timeoutMillis = 5_000) {
            tags.all { rule.onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        }
        (0 until TangoDraft.N * TangoDraft.N).map { "tango.editor.cell.$it.forced" }.filter { it !in tags }
            .forEach { rule.onAllNodesWithTag(it, useUnmergedTree = true).assertCountEquals(0) }
    }

    @Test
    fun `the view starts off and toggles`() {
        assertFalse(controller.forcedView.value)

        controller.toggleForcedView()
        assertTrue(controller.forcedView.value)

        controller.toggleForcedView()
        assertFalse(controller.forcedView.value)
    }

    @Test
    fun `on, the board tags exactly the forced cells, and off removes them`() {
        show()
        assertTrue(forcedTags().isNotEmpty())
        assertTagged(emptySet())

        controller.toggleForcedView()
        assertTagged(forcedTags())
        forcedCount().assertCountEquals(1)

        controller.toggleForcedView()
        rule.waitUntil(timeoutMillis = 5_000) { forcedCount().fetchSemanticsNodes().isEmpty() }
        assertTagged(emptySet())
    }

    @Test
    fun `the tool strip chip flips the controller`() {
        rule.setContent { PuzzleBoxTheme { TangoEditorUi.ToolHint(controller, Modifier) } }

        rule.onNodeWithTag("tango.editor.forced.toggle").performClick()
        assertTrue(controller.forcedView.value)

        rule.onNodeWithTag("tango.editor.forced.toggle").performClick()
        assertFalse(controller.forcedView.value)
    }

    @Test
    fun `a tap on a forced cell cycles it and adds one undo step`() {
        show()
        controller.toggleForcedView()
        val cell = TangoForcedCells.of(board()).keys.first()
        assertTagged(forcedTags())
        assertFalse(controller.draft.value.canUndo)

        rule.onNodeWithTag("tango.editor.board").performTouchInput {
            down(centre(cell))
            up()
        }

        assertEquals(TangoCell.SUN, board().givens[cell])
        assertEquals(1, edits.size)
        assertTrue(controller.draft.value.canUndo)
        assertTrue(edits.single().undo()?.canUndo == false)
    }

    private fun TouchInjectionScope.centre(cell: Int) = Offset(
        x = (cell % TangoDraft.N + 0.5f) * width / TangoDraft.N,
        y = (cell / TangoDraft.N + 0.5f) * height / TangoDraft.N,
    )
}
