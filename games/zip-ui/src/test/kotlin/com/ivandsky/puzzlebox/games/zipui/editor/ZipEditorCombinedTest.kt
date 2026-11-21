package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
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
 * The Numbers & walls step through its tags: a tap in the middle of a cell numbers it, a tap near an
 * inside edge draws its wall, with no mode change between them; each tap is one undo step. Taps are
 * in cell units from the board's top-left, on a 5×5 board with the path along the top row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class ZipEditorCombinedTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()

    private fun show(walls: Set<Wall> = emptySet()): ZipEditorController {
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
        rule.onNodeWithTag(COMBINED_TAB).performClick()
        rule.waitForIdle()
        return controller
    }

    private fun tapAt(x: Float, y: Float) {
        rule.onNodeWithTag(BOARD).performTouchInput { click(Offset(x * width / SIZE, y * height / SIZE)) }
        rule.waitForIdle()
    }

    private fun tapMiddle(cell: Int) = tapAt(cell % SIZE + 0.5f, cell / SIZE + 0.5f)

    /** The right side of [cell], [off] cells towards the cell from the line. */
    private fun tapRight(cell: Int, off: Float = 0f) = tapAt(cell % SIZE + 1f - off, cell / SIZE + 0.5f)

    /** The bottom side of [cell], [off] cells towards the cell from the line. */
    private fun tapBottom(cell: Int, off: Float = 0f) = tapAt(cell % SIZE + 0.5f, cell / SIZE + 1f - off)

    private fun exists(tag: String) =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun board(draft: EditorDraft<*>) = draft.state.draft as ZipDraft

    private fun right(cell: Int) = Wall(Cell(cell), WallDirection.RIGHT)

    private fun bottom(cell: Int) = Wall(Cell(cell), WallDirection.DOWN)

    @Test
    fun `the tabs are Path and Numbers and walls`() {
        show()

        assertTrue(exists("zip.editor.step.path"))
        assertTrue(exists(COMBINED_TAB))
        rule.onNodeWithTag(COMBINED_TAB).assertTextEquals("Numbers & walls")
    }

    @Test
    fun `a tap in a cell's middle numbers it and a tap on an edge draws a wall with no mode change`() {
        val controller = show()
        tapMiddle(2)
        tapRight(7)

        assertEquals(listOf(0, 2, 4), controller.shown.value.waypoints)
        assertEquals(setOf(right(7)), controller.shown.value.walls)
        assertEquals(ZipEditorStep.NumbersAndWalls, controller.step.value)
        assertTrue(edits[0].state.history.done.last() is ZipDraftCommand.ToggleMark)
        assertTrue(edits[1].state.history.done.last() is ZipDraftCommand.ToggleWall)
    }

    @Test
    fun `a tap on the edge of a numbered cell leaves its number, a tap in its middle removes it`() {
        val controller = show()
        tapMiddle(2)
        tapBottom(2, off = 0.1f)

        assertEquals(listOf(0, 2, 4), controller.shown.value.waypoints)
        assertEquals(setOf(bottom(2)), controller.shown.value.walls)

        tapMiddle(2)

        assertEquals(listOf(0, 4), controller.shown.value.waypoints)
        assertEquals(setOf(bottom(2)), controller.shown.value.walls)
    }

    @Test
    fun `the edge band is wider than 0 point 2 of a cell and narrower than 0 point 3`() {
        val controller = show()
        tapBottom(2, off = 0.3f)
        assertEquals(listOf(0, 2, 4), controller.shown.value.waypoints)
        assertTrue(controller.shown.value.walls.isEmpty())

        tapBottom(2, off = 0.2f)
        assertEquals(setOf(bottom(2)), controller.shown.value.walls)
    }

    @Test
    fun `a tap on the board's outer border numbers the cell, there is no wall to draw there`() {
        val controller = show()
        tapAt(2.5f, 0.05f)

        assertEquals(listOf(0, 2, 4), controller.shown.value.waypoints)
        assertTrue(controller.shown.value.walls.isEmpty())
    }

    @Test
    fun `a wall tap between two path cells is refused with a shake and is not read as a number`() {
        val controller = show()
        assertNull(controller.shaken.value)
        tapRight(1)

        assertNotNull(controller.shaken.value)
        assertTrue(controller.shown.value.walls.isEmpty())
        assertEquals(listOf(0, 4), controller.shown.value.waypoints)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `a tap in the middle of an end cell or off the path changes nothing`() {
        val controller = show()
        tapMiddle(0)
        assertEquals(0, controller.shaken.value?.cell)
        tapMiddle(12)

        assertEquals(listOf(0, 4), controller.shown.value.waypoints)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `undo and redo walk back and forth across numbers and walls`() {
        show()
        tapMiddle(2)
        tapRight(7)
        tapMiddle(1)

        val last = edits.last()
        assertEquals(listOf(0, 1, 2, 4), board(last).waypoints)
        assertEquals(setOf(right(7)), board(last).walls)

        val noOne = last.undo()!!
        assertEquals(listOf(0, 2, 4), board(noOne).waypoints)
        assertEquals(setOf(right(7)), board(noOne).walls)
        val noWall = noOne.undo()!!
        assertEquals(listOf(0, 2, 4), board(noWall).waypoints)
        assertTrue(board(noWall).walls.isEmpty())
        val noTwo = noWall.undo()!!
        assertEquals(listOf(0, 4), board(noTwo).waypoints)

        val again = noTwo.redo()!!.redo()!!
        assertEquals(listOf(0, 2, 4), board(again).waypoints)
        assertEquals(setOf(right(7)), board(again).walls)
        assertEquals(listOf(0, 1, 2, 4), board(again.redo()!!).waypoints)
    }

    @Test
    fun `Clear walls shows here, clears every wall as one step and undo restores them`() {
        val controller = show(walls = setOf(right(7), bottom(12), bottom(6)))
        rule.onNodeWithTag("zip.editor.clearWalls").performClick()
        rule.waitForIdle()

        assertTrue(controller.shown.value.walls.isEmpty())
        assertEquals(1, edits.size)
        assertTrue(edits.single().state.history.done.single() is ZipDraftCommand.ClearWalls)
        assertEquals(setOf(right(7), bottom(12), bottom(6)), board(edits.single().undo()!!).walls)
        assertTrue(!exists("zip.editor.reverse"))
        assertTrue(!exists("zip.editor.clearPath"))
    }

    @Test
    fun `dragging in the combined step draws no path`() {
        val controller = show()
        rule.onNodeWithTag(BOARD).performTouchInput {
            down(Offset(0.5f * width / SIZE, 2.5f * height / SIZE))
            moveTo(Offset(1.5f * width / SIZE, 2.5f * height / SIZE))
            moveTo(Offset(2.5f * width / SIZE, 2.5f * height / SIZE))
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(0, 1, 2, 3, 4), controller.shown.value.path)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `the Next line reads Numbers and walls on a covered board and goes once the author is there`() {
        val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
        val covered = (blank.state.draft as ZipDraft).copy(path = (0 until SIZE * SIZE).toList(), waypoints = listOf(0, 24))
        val controller = ZipEditorController(EditorDraft(ZipEditor, blank.state.copy(draft = covered))) {}
        rule.setContent { PuzzleBoxTheme { ZipEditorHint(controller) } }

        rule.onNodeWithTag("zip.editor.hint.next").assertTextEquals("Next: Numbers & walls")

        rule.runOnIdle { controller.select(ZipEditorStep.NumbersAndWalls) }
        rule.onNodeWithTag("zip.editor.hint.next").assertDoesNotExist()
    }

    private companion object {
        const val SIZE = 5
        const val BOARD = "zip.editor.board"
        const val COMBINED_TAB = "zip.editor.step.numbersandwalls"
    }
}
