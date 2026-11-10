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
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A real drag on the selected path's tail, through the board's own touch: back two cells, then
 * out to a new neighbour.
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

    /**
     * Path (1,0),(1,1); another word holds (0,2). Turning the tail 45° north-east onto (0,2)
     * leaves the word alone: the end stays on (1,1) with its letter, and (0,1), a cell the
     * finger never touched, is never added.
     */
    @Test
    fun `turning the tail onto a cell another word holds and on leaves the word as it was`() {
        listOf(6, 7).forEach(controller::traceTo)
        controller.endTrace()
        listOf(2, 3).forEach(controller::traceTo)
        controller.endTrace()
        controller.selectPath(0)
        controller.typeWord("ab")
        val before = board()
        val steps = controller.draft.value.state.history.done.size
        show()

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(cellPoint(1.5f, 1.5f))
            moveTo(cellPoint(2.0f, 1.0f))
            moveTo(cellPoint(2.5f, 0.5f))
            moveTo(cellPoint(2.7f, 0.4f))
            moveTo(cellPoint(2.9f, 0.3f))
        }
        rule.waitForIdle()
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
        rule.waitForIdle()

        assertEquals(listOf(6, 7), board().paths[0].cells)
        assertEquals(before.letters, board().letters)
        assertEquals(steps, controller.draft.value.state.history.done.size)
        assertEquals(0, controller.selectedPath.value)
    }

    /**
     * Path (1,0),(1,1), tail grabbed; a swipe along row 1, 0.3 off the cells' centres, at 0.9
     * pitch per event. It jumps (1,5)'s circle between two events (x 5.1 then 5.9), yet the end
     * still takes every cell it crosses.
     */
    @Test
    fun `a fast swipe along a row extends by every cell it crosses`() {
        listOf(6, 7).forEach(controller::traceTo)
        controller.endTrace()
        controller.selectPath(0)
        show()

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(cellPoint(1.5f, 1.8f))
            listOf(2.4f, 3.3f, 4.2f, 5.1f, 5.9f).forEach { moveTo(cellPoint(it, 1.8f)) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
        rule.waitForIdle()

        assertEquals(listOf(6, 7, 8, 9, 10, 11), board().paths.single().cells)
    }

    /**
     * Path (1,0),(1,1),(2,1), tail grabbed; the finger goes on diagonally to (3,2), the line
     * bulging 0.3 pitch toward the free side cell (2,2). Only the diagonal cell is added: the
     * side cell it grazed is no zig-zag.
     */
    @Test
    fun `a diagonal bulging toward a free side cell extends only the diagonal cell`() {
        listOf(6, 7, 13).forEach(controller::traceTo)
        controller.endTrace()
        controller.selectPath(0)
        show()

        rule.onNodeWithTag(BOARD).performTouchInput {
            down(cellPoint(1.5f, 2.5f))
            // Off the line from (1.5, 2.5) to (2.5, 3.5) by 0.3·sin(πt) toward (2.5, 2.5).
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { t ->
                val bulge = 0.3f * sin(PI.toFloat() * t) / sqrt(2f)
                moveTo(cellPoint(1.5f + t + bulge, 2.5f + t - bulge))
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
        rule.waitForIdle()

        assertEquals(listOf(6, 7, 13, 20), board().paths.single().cells)
    }

    private fun show() {
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier)
            }
        }
        rule.waitForIdle()
    }

    private fun TouchInjectionScope.cellPoint(colCells: Float, rowCells: Float): Offset {
        val cell = height / StrandsDraft.ROWS
        return Offset((width - cell * StrandsDraft.COLS) / 2 + colCells * cell, rowCells * cell)
    }

    private companion object {
        const val BOARD = "strands.editor.board"
    }
}
