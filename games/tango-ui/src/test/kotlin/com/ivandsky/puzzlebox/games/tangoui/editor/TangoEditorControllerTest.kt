package com.ivandsky.puzzlebox.games.tangoui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tap on a cell is one undoable [com.ivandsky.puzzlebox.game.editor.ReplaceDraft] step. */
class TangoEditorControllerTest {

    private val edits = ArrayList<EditorDraft<*>>()
    private val controller = TangoEditorController(EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())) { edits += it }

    private fun givens(draft: EditorDraft<*>): Map<Int, TangoCell> = (draft.state.draft as TangoDraft).givens

    @Test
    fun `placing sets a given and reports one edit`() {
        controller.place(3, TangoCell.SUN)

        assertEquals(mapOf(3 to TangoCell.SUN), givens(controller.draft.value))
        assertEquals(1, edits.size)
    }

    @Test
    fun `placing null empties the cell`() {
        controller.place(3, TangoCell.MOON)
        controller.place(3, null)

        assertTrue(givens(controller.draft.value).isEmpty())
        assertEquals(2, edits.size)
    }

    @Test
    fun `placing what the cell already holds is no edit`() {
        controller.place(3, null)

        assertTrue(edits.isEmpty())
    }

    @Test
    fun `each placement is one undo step`() {
        controller.place(3, TangoCell.SUN)
        controller.place(4, TangoCell.MOON)

        val undone = checkNotNull(controller.draft.value.undo())
        assertEquals(mapOf(3 to TangoCell.SUN), givens(undone))
    }
}
