package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftCommand
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Path step through its tags: extend from either end, backtrack, Reverse, Clear path; each is one undo step. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class ZipEditorPathTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()

    private fun show(path: List<Int> = emptyList(), marks: List<Int> = emptyList()): ZipEditorController {
        val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
        val ends = listOfNotNull(path.firstOrNull(), path.lastOrNull().takeIf { path.size > 1 })
        val board = (blank.state.draft as ZipDraft).copy(path = path, waypoints = ends.take(1) + marks + ends.drop(1))
        val controller = ZipEditorController(EditorDraft(ZipEditor, blank.state.copy(draft = board))) { edits += it }
        rule.setContent {
            PuzzleBoxTheme {
                Column(Modifier.fillMaxSize()) {
                    ZipEditorUi.ToolHint(controller, Modifier.fillMaxWidth())
                    ZipEditorUi.Board(controller, Verdict.Checking, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        return controller
    }

    private fun TouchInjectionScope.centre(cell: Int) = Offset(
        x = (cell % SIZE + 0.5f) * width / SIZE,
        y = (cell / SIZE + 0.5f) * height / SIZE,
    )

    /** Presses on the first cell, passes over each of [cells] in turn, then lifts. */
    private fun drag(vararg cells: Int) {
        rule.onNodeWithTag(BOARD).performTouchInput {
            down(centre(cells.first()))
            cells.toList().zipWithNext().forEach { (from, to) ->
                for (step in 1..STEPS) moveTo(centre(from) + (centre(to) - centre(from)) * (step / STEPS.toFloat()))
            }
            up()
        }
        rule.waitForIdle()
    }

    private fun board(draft: EditorDraft<*>) = draft.state.draft as ZipDraft

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `every cell carries its tag`() {
        show()
        for (cell in 0 until SIZE * SIZE) rule.onAllNodesWithTag("zip.editor.cell.$cell").assertCountEquals(1)
    }

    @Test
    fun `a drag draws a path and lifting commits one step`() {
        val controller = show()
        drag(0, 1, 2, 7)

        assertEquals(listOf(0, 1, 2, 7), controller.shown.value.path)
        val edit = edits.single()
        assertEquals(listOf(0, 7), board(edit).waypoints)
        assertTrue(edit.state.history.done.single() is ZipDraftCommand.PathEdit)
        assertEquals(emptyList<Int>(), board(edit.undo()!!).path)
    }

    @Test
    fun `a drag from the tail extends the path`() {
        val controller = show(path = listOf(0, 1))
        drag(1, 2, 7)

        assertEquals(listOf(0, 1, 2, 7), controller.shown.value.path)
        assertEquals(1, edits.size)
    }

    @Test
    fun `a drag from the head extends the path at the head`() {
        val controller = show(path = listOf(6, 7))
        drag(6, 1, 0)

        assertEquals(listOf(0, 1, 6, 7), controller.shown.value.path)
        assertEquals(listOf(0, 7), controller.shown.value.waypoints)
        assertEquals(1, edits.size)
    }

    @Test
    fun `dragging back over the path backtracks one cell at a time`() {
        val controller = show(path = listOf(0, 1, 2, 7))
        drag(7, 2, 1)

        assertEquals(listOf(0, 1), controller.shown.value.path)
        assertEquals(listOf(0, 1), board(edits.single()).waypoints)
    }

    @Test
    fun `dragging back from the head retracts the head`() {
        val controller = show(path = listOf(0, 1, 2, 7))
        drag(0, 1, 2)

        assertEquals(listOf(2, 7), controller.shown.value.path)
    }

    @Test
    fun `Reverse flips the path and its numbers in one step`() {
        val controller = show(path = listOf(0, 1, 2, 7, 12), marks = listOf(2))
        tap("zip.editor.reverse")

        assertEquals(listOf(12, 7, 2, 1, 0), controller.shown.value.path)
        assertEquals(listOf(12, 2, 0), controller.shown.value.waypoints)
        val edit = edits.single()
        assertTrue(edit.state.history.done.single() is ZipDraftCommand.PathEdit)
        assertEquals(listOf(0, 1, 2, 7, 12), board(edit.undo()!!).path)
        assertEquals(listOf(0, 2, 12), board(edit.undo()!!).waypoints)
    }

    @Test
    fun `Clear path empties the path in one step`() {
        val controller = show(path = listOf(0, 1, 2))
        tap("zip.editor.clearPath")

        assertEquals(emptyList<Int>(), controller.shown.value.path)
        val edit = edits.single()
        assertEquals(listOf(0, 1, 2), board(edit.undo()!!).path)
        assertNull(edit.undo()!!.undo())
    }

    @Test
    fun `Reverse and Clear path on an empty path are no edit`() {
        show()
        tap("zip.editor.reverse")
        tap("zip.editor.clearPath")

        assertTrue(edits.isEmpty())
    }

    @Test
    fun `the hint shows the covered cell count`() {
        show(path = listOf(0, 1, 2))
        rule.onNodeWithTag("zip.editor.hint").assertTextContains("3", substring = true)
        rule.onNodeWithTag("zip.editor.hint").assertTextContains("${SIZE * SIZE}", substring = true)

        drag(2, 7, 12)

        rule.onNodeWithTag("zip.editor.hint").assertTextContains("5 of", substring = true)
    }

    @Test
    fun `a covered board names the next step in the hint`() {
        val snake = (0 until SIZE).flatMap { row -> (0 until SIZE).map { row * SIZE + if (row % 2 == 0) it else SIZE - 1 - it } }
        show(path = snake)

        rule.onAllNodesWithTag("zip.editor.hint.next").assertCountEquals(1)
    }

    @Test
    fun `an incomplete board names no next step`() {
        show(path = listOf(0, 1))

        rule.onAllNodesWithTag("zip.editor.hint.next").assertCountEquals(0)
    }

    @Test
    fun `the step tabs select their step and Path is first`() {
        val controller = show()
        assertEquals(ZipEditorStep.Path, controller.step.value)

        tap("zip.editor.step.numbers")
        assertEquals(ZipEditorStep.Numbers, controller.step.value)
        tap("zip.editor.step.walls")
        assertEquals(ZipEditorStep.Walls, controller.step.value)
        tap("zip.editor.step.path")
        assertEquals(ZipEditorStep.Path, controller.step.value)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `Reverse and Clear path show in the Path step only`() {
        show(path = listOf(0, 1))
        tap("zip.editor.step.numbers")

        rule.onAllNodesWithTag("zip.editor.reverse").assertCountEquals(0)
        rule.onAllNodesWithTag("zip.editor.clearPath").assertCountEquals(0)
    }

    @Test
    fun `dragging outside the Path step draws nothing`() {
        val controller = show()
        tap("zip.editor.step.numbers")
        drag(0, 1, 2)

        assertEquals(emptyList<Int>(), controller.shown.value.path)
        assertTrue(edits.isEmpty())
    }

    private companion object {
        const val SIZE = 5
        const val STEPS = 6
        const val BOARD = "zip.editor.board"
    }
}
