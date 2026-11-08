package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsEditorControllerTest {

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) { edits += it }

    private fun board() = controller.draft.value.state.draft as StrandsDraft

    private fun trace(vararg cells: Int) = cells.forEach(controller::traceTo)

    @Test
    fun `lifting a two-cell trace commits one path with the typed word on its blank cells`() {
        controller.typeWord("go")
        trace(0, 1)
        controller.endTrace()

        assertEquals(1, edits.size)
        assertEquals(listOf(0, 1), board().paths.single().cells)
        assertEquals("GO", board().paths.single().word)
        assertEquals("GO", board().letters.take(2))
        assertTrue(controller.trace.value.isEmpty())
        assertEquals("", controller.word.value)
    }

    @Test
    fun `a word shorter than the trace leaves the path unnamed`() {
        controller.typeWord("g")
        trace(0, 1)
        controller.endTrace()

        assertNull(board().paths.single().word)
    }

    @Test
    fun `a lone cell lifts without an edit`() {
        trace(0)
        controller.endTrace()

        assertTrue(edits.isEmpty())
        assertTrue(board().paths.isEmpty())
    }

    @Test
    fun `a cell that is not adjacent does not join the trace`() {
        trace(0, 3)

        assertEquals(listOf(0), controller.trace.value)
    }

    @Test
    fun `a cell a path already holds cannot be traced`() {
        trace(0, 1)
        controller.endTrace()
        controller.traceTo(1)

        assertTrue(controller.trace.value.isEmpty())
    }

    @Test
    fun `the commit is one undoable step`() {
        trace(0, 1)
        controller.endTrace()

        val undone = controller.draft.value.undo()
        assertTrue(undone != null && (undone.state.draft as StrandsDraft).paths.isEmpty())
    }

    @Test
    fun `typed word keeps only letters A to Z, upper case`() {
        controller.typeWord("c-a\u00E9f\u00C9e1")

        assertEquals("CAFE", controller.word.value)
    }

    @Test
    fun `a committed clue loses its line breaks`() {
        controller.setClue("a\nb\r\nc\rd")

        assertEquals("abcd", board().clue)
    }

    @Test
    fun `a draft built from typed CAFE-acute and a pasted clue prints text that parses back`() {
        controller.typeWord("CAF\u00C9")
        trace(0, 1, 2)
        controller.endTrace()
        controller.setClue("a\nb")

        val draft = board()
        val body = StrandsEditor.print(draft).lines().drop(1).joinToString("\n")
        assertEquals(NotationResult.Ok(draft), StrandsEditor.parse(body))
    }
}
