package com.ivandsky.puzzlebox.games.tangoui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tap on a cell is one undoable step. */
class TangoEditorControllerTest {

    private val edits = ArrayList<EditorDraft<*>>()
    private val controller = TangoEditorController(EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())) { edits += it }

    private fun givens(draft: EditorDraft<*>): Map<Int, TangoCell> = (draft.state.draft as TangoDraft).givens

    private fun tap(cell: Int) {
        controller.press(TangoEditorTarget.Square(cell))
        controller.release()
    }

    @Test
    fun `a tap sets a sun and reports one edit`() {
        tap(3)

        assertEquals(mapOf(3 to TangoCell.SUN), givens(controller.draft.value))
        assertEquals(1, edits.size)
    }

    @Test
    fun `three taps cycle the cell back to empty`() {
        repeat(3) { tap(3) }

        assertTrue(givens(controller.draft.value).isEmpty())
        assertEquals(3, edits.size)
    }

    @Test
    fun `each tap is one undo step`() {
        tap(3)
        tap(4)

        val undone = checkNotNull(controller.draft.value.undo())
        assertEquals(mapOf(3 to TangoCell.SUN), givens(undone))
    }
}
