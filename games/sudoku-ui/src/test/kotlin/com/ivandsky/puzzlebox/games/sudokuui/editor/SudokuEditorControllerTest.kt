package com.ivandsky.puzzlebox.games.sudokuui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuEditorControllerTest {

    private val editor = SudokuEditor(SudokuGames.MINI)
    private val edits = ArrayList<EditorDraft<*>>()

    private fun controller() = SudokuEditorController(EditorDraft.fresh(editor, editor.variants.first())) { edits += it }

    private fun givensOf(draft: EditorDraft<*>): List<Int> = (draft.state.draft as SudokuDraft).givens

    @Test
    fun `a tap selects a cell and a second tap clears it`() {
        val controller = controller()

        controller.tap(4)
        assertEquals(4, controller.selected.value)
        controller.tap(4)
        assertNull(controller.selected.value)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `a digit with no cell selected is no edit`() {
        controller().digit(3)

        assertTrue(edits.isEmpty())
    }

    @Test
    fun `a digit places on the selected cell as one undoable step`() {
        val controller = controller()
        controller.tap(7)

        controller.digit(5)

        assertEquals(1, edits.size)
        assertEquals(5, givensOf(edits.single())[7])
        assertEquals(givensOf(edits.single()), givensOf(controller.draft.value))
        assertEquals(7, controller.selected.value)
        assertTrue(edits.single().canUndo)
        assertEquals(List(36) { 0 }, givensOf(edits.single().undo()!!))
    }

    @Test
    fun `digit zero erases and an unchanged digit is no edit`() {
        val controller = controller()
        controller.tap(0)
        controller.digit(2)
        controller.digit(2)
        assertEquals(1, edits.size)

        controller.digit(0)

        assertEquals(2, edits.size)
        assertEquals(0, givensOf(edits.last())[0])
    }

    @Test
    fun `a digit past the board size is refused`() {
        val controller = controller()
        controller.tap(0)

        val refused = runCatching { controller.digit(7) }

        assertTrue(refused.isFailure)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `adopting a draft keeps the selection and shows the draft`() {
        val controller = controller()
        controller.tap(3)
        controller.digit(1)
        val undone = edits.single().undo()!!

        controller.adopt(undone)

        assertEquals(List(36) { 0 }, givensOf(controller.draft.value))
        assertNotNull(controller.selected.value)
    }
}
