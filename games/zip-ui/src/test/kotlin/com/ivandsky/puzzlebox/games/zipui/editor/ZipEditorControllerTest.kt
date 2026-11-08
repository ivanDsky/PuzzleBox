package com.ivandsky.puzzlebox.games.zipui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftCommand
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One stroke is one undoable step; the path's ends are waypoints 1 and last. On a 5×5 board, cell = row * 5 + col. */
class ZipEditorControllerTest {

    private val edits = ArrayList<EditorDraft<*>>()

    private fun controller(walls: Set<Wall> = emptySet(), path: List<Int> = emptyList()): ZipEditorController {
        val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
        val board = ZipDraft(5, walls, path, listOfNotNull(path.firstOrNull(), path.lastOrNull().takeIf { path.size > 1 }))
        return ZipEditorController(EditorDraft(ZipEditor, blank.state.copy(draft = board))) { edits += it }
    }

    private fun ZipEditorController.stroke(vararg cells: Int) {
        beginStroke(cells.first())
        cells.drop(1).forEach(::strokeTo)
        endStroke()
    }

    private fun board(draft: EditorDraft<*>) = draft.state.draft as ZipDraft

    @Test
    fun `a stroke draws a path whose ends are waypoints 1 and last`() {
        val controller = controller()
        controller.stroke(0, 1, 2, 7)

        assertEquals(listOf(0, 1, 2, 7), controller.shown.value.path)
        assertEquals(listOf(0, 7), controller.shown.value.waypoints)
    }

    @Test
    fun `a stroke is one undoable step`() {
        val controller = controller()
        controller.stroke(0, 1, 2, 7)

        val edit = edits.single()
        assertEquals(listOf(0, 1, 2, 7), board(edit).path)
        assertTrue(edit.state.history.done.single() is ZipDraftCommand.PathEdit)
        assertEquals(emptyList<Int>(), board(edit.undo()!!).path)
    }

    @Test
    fun `drawing back over the path retracts one cell at a time`() {
        val controller = controller()
        controller.stroke(0, 1, 2, 1, 0)

        assertEquals(listOf(0), controller.shown.value.path)
    }

    @Test
    fun `a wall stops the stroke and a diagonal jump is ignored`() {
        val controller = controller(walls = setOf(Wall(Cell(1), WallDirection.RIGHT)))
        controller.stroke(0, 1, 2, 7)

        assertEquals(listOf(0, 1), controller.shown.value.path)
    }

    @Test
    fun `a stroke may not cross the path`() {
        val controller = controller()
        controller.stroke(0, 1, 6, 5, 0)

        assertEquals(listOf(0, 1, 6, 5), controller.shown.value.path)
    }

    @Test
    fun `a stroke starting on the tail carries the path on`() {
        val controller = controller(path = listOf(0, 1))
        controller.stroke(1, 2)

        assertEquals(listOf(0, 1, 2), controller.shown.value.path)
        assertEquals(listOf(0, 2), controller.shown.value.waypoints)
    }

    @Test
    fun `a stroke starting mid path cuts the path back to that cell`() {
        val controller = controller(path = listOf(0, 1, 2, 3))
        controller.stroke(1, 6)

        assertEquals(listOf(0, 1, 6), controller.shown.value.path)
    }

    @Test
    fun `a stroke that never leaves its cell is no edit`() {
        val controller = controller()
        controller.stroke(12)

        assertTrue(edits.isEmpty())
        assertEquals(emptyList<Int>(), controller.shown.value.path)
    }

    @Test
    fun `a stroke back to where it began is no edit`() {
        val controller = controller(path = listOf(0, 1))
        controller.stroke(1, 2, 1)

        assertTrue(edits.isEmpty())
    }

    @Test
    fun `adopting a draft shows it`() {
        val controller = controller()
        val other = controller(path = listOf(3, 4)).draft.value
        controller.adopt(other)

        assertEquals(listOf(3, 4), controller.shown.value.path)
    }
}
