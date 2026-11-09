package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A real drag on the selected path's tail, through the board's own touch: back two cells, then
 * out to a new neighbour; and the end rings that mark where such a drag starts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class StrandsEditorDragEndGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) { edits += it }

    private fun board() = controller.draft.value.state.draft as StrandsDraft

    @Test
    fun `dragging the tail back two cells then onto a new neighbour reshapes the path`() {
        listOf(0, 1, 2, 3).forEach(controller::traceTo)
        controller.endTrace()
        controller.selectPath(0)
        controller.typeWord("star")
        val steps = controller.draft.value.state.history.done.size
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(cellPoint(3.5f, 0.5f))
            moveTo(cellPoint(2.5f, 0.5f))
            moveTo(cellPoint(1.5f, 0.5f))
            moveTo(cellPoint(1.5f, 1.5f))
        }
        rule.waitForIdle()
        assertEquals(listOf(0, 1, 7), controller.trace.value)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
        rule.waitForIdle()

        assertEquals(listOf(0, 1, 7), board().paths.single().cells)
        assertEquals("ST..", board().letters.take(4))
        assertEquals(0, controller.selectedPath.value)
        assertTrue(controller.trace.value.isEmpty())
        assertEquals(steps + 1, controller.draft.value.state.history.done.size)
    }

    @Test
    fun `the selected path wears a ring on both ends only while selected`() {
        listOf(0, 1, 2, 3).forEach(controller::traceTo)
        controller.endTrace()
        controller.selectPath(0)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("strands.editor.cell.0.end").assertExists()
        rule.onNodeWithTag("strands.editor.cell.3.end").assertExists()
        listOf(1, 2).forEach { rule.onNodeWithTag("strands.editor.cell.$it.end").assertDoesNotExist() }

        controller.selectPath(null)
        rule.waitForIdle()
        listOf(0, 3).forEach { rule.onNodeWithTag("strands.editor.cell.$it.end").assertDoesNotExist() }
    }

    private fun TouchInjectionScope.cellPoint(colCells: Float, rowCells: Float): Offset {
        val cell = height / StrandsDraft.ROWS
        return Offset((width - cell * StrandsDraft.COLS) / 2 + colCells * cell, rowCells * cell)
    }

    private companion object {
        const val BOARD = "strands.editor.board"
    }
}
