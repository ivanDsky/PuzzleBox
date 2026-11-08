package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftCommand
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
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

/**
 * The Walls step through its tags: tap near the line between two cells to toggle a wall, a wall
 * that would cut the path is refused with a shake, Clear walls is one step, each tap is one undo
 * step.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class ZipEditorWallsTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()

    /** The path 0, 1, 2, 3, 4 along the top row, shown in the Walls step. */
    private fun show(walls: Set<Wall> = emptySet(), step: String = "walls"): ZipEditorController {
        val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
        val board = (blank.state.draft as ZipDraft).copy(
            path = listOf(0, 1, 2, 3, 4),
            waypoints = listOf(0, 4),
            walls = walls,
        )
        val controller = ZipEditorController(EditorDraft(ZipEditor, blank.state.copy(draft = board))) { edits += it }
        rule.setContent {
            PuzzleBoxTheme { ZipEditorBoard(controller, Modifier.fillMaxSize()) }
        }
        rule.onNodeWithTag("zip.editor.step.$step").performClick()
        rule.waitForIdle()
        return controller
    }

    private fun TouchInjectionScope.centre(cell: Int) = Offset(
        x = (cell % SIZE + 0.5f) * width / SIZE,
        y = (cell / SIZE + 0.5f) * height / SIZE,
    )

    private fun TouchInjectionScope.rightEdge(cell: Int, off: Float = 0f) =
        centre(cell) + Offset(width / SIZE / 2f + off * width / SIZE, 0f)

    private fun TouchInjectionScope.bottomEdge(cell: Int) = centre(cell) + Offset(0f, height / SIZE / 2f)

    private fun tapRight(cell: Int, off: Float = 0f) {
        rule.onNodeWithTag(BOARD).performTouchInput { click(rightEdge(cell, off)) }
        rule.waitForIdle()
    }

    private fun tapBottom(cell: Int) {
        rule.onNodeWithTag(BOARD).performTouchInput { click(bottomEdge(cell)) }
        rule.waitForIdle()
    }

    private fun hasWallTag(tag: String) =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun board(draft: EditorDraft<*>) = draft.state.draft as ZipDraft

    private fun right(cell: Int) = Wall(Cell(cell), WallDirection.RIGHT)

    private fun bottom(cell: Int) = Wall(Cell(cell), WallDirection.DOWN)

    @Test
    fun `tapping a right edge places a wall and tags it`() {
        val controller = show()
        tapRight(7)

        assertEquals(setOf(right(7)), controller.shown.value.walls)
        assertTrue(hasWallTag("zip.editor.edge.7.right"))
    }

    @Test
    fun `tapping a bottom edge places a wall and tags it`() {
        val controller = show()
        tapBottom(7)

        assertEquals(setOf(bottom(7)), controller.shown.value.walls)
        assertTrue(hasWallTag("zip.editor.edge.7.bottom"))
    }

    @Test
    fun `tapping a wall again removes it`() {
        val controller = show(walls = setOf(right(7)))
        assertTrue(hasWallTag("zip.editor.edge.7.right"))
        tapRight(7)

        assertTrue(controller.shown.value.walls.isEmpty())
        assertTrue(!hasWallTag("zip.editor.edge.7.right"))
    }

    @Test
    fun `the band reaches 0·35 of a cell from the line but not 0·42`() {
        val controller = show()
        tapRight(7, off = 0.42f)
        assertTrue(controller.shown.value.walls.isEmpty())
        assertTrue(edits.isEmpty())

        tapRight(7, off = 0.35f)
        assertEquals(setOf(right(7)), controller.shown.value.walls)
    }

    @Test
    fun `a tap at the grid end of an edge still hits it`() {
        val controller = show()
        rule.onNodeWithTag(BOARD).performTouchInput {
            click(Offset(3f * width / SIZE, 4.95f * height / SIZE))
        }
        rule.waitForIdle()

        assertEquals(setOf(right(22)), controller.shown.value.walls)
    }

    @Test
    fun `taps in the Path step place no walls`() {
        val controller = show(step = "path")
        tapRight(7)

        assertTrue(controller.shown.value.walls.isEmpty())
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `a wall across a path step is refused with a shake and no edit`() {
        val controller = show()
        assertNull(controller.shaken.value)
        tapRight(1)

        assertTrue(controller.shown.value.walls.isEmpty())
        assertNotNull(controller.shaken.value)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `a wall off the path is allowed beside it`() {
        val controller = show()
        tapBottom(1)

        assertEquals(setOf(bottom(1)), controller.shown.value.walls)
        assertNull(controller.shaken.value)
    }

    @Test
    fun `each tap is one undo step`() {
        show()
        tapRight(7)
        tapBottom(12)

        assertEquals(2, edits.size)
        assertTrue(edits.first().state.history.done.single() is ZipDraftCommand.ToggleWall)
        assertTrue(board(edits.first().undo()!!).walls.isEmpty())
        assertEquals(setOf(right(7)), board(edits.last().undo()!!).walls)
    }

    @Test
    fun `clear walls removes them all as one step and undo restores them`() {
        val controller = show(walls = setOf(right(7), bottom(12), bottom(6)))
        rule.onNodeWithTag("zip.editor.clearWalls").performClick()
        rule.waitForIdle()

        assertTrue(controller.shown.value.walls.isEmpty())
        assertEquals(1, edits.size)
        assertTrue(edits.single().state.history.done.single() is ZipDraftCommand.ClearWalls)
        assertEquals(setOf(right(7), bottom(12), bottom(6)), board(edits.single().undo()!!).walls)
    }

    @Test
    fun `clear walls with no walls changes nothing`() {
        show()
        rule.onNodeWithTag("zip.editor.clearWalls").performClick()
        rule.waitForIdle()

        assertTrue(edits.isEmpty())
    }

    private companion object {
        const val SIZE = 5
        const val BOARD = "zip.editor.board"
    }
}
