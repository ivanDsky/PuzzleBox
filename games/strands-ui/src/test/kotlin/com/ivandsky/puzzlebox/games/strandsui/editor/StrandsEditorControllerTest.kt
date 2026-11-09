package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsEditorControllerTest {

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) { edits += it }

    private fun board() = controller.draft.value.state.draft as StrandsDraft

    private fun trace(vararg cells: Int) = cells.forEach(controller::traceTo)

    @Test
    fun `lifting a two-cell trace commits one blank path and selects it`() {
        trace(0, 1)
        controller.endTrace()

        assertEquals(1, edits.size)
        assertEquals(listOf(0, 1), board().paths.single().cells)
        assertEquals(StrandsDraft.BLANK.toString().repeat(2), board().letters.take(2))
        assertNull(board().paths.single().word)
        assertTrue(controller.trace.value.isEmpty())
        assertEquals(0, controller.selectedPath.value)
        assertEquals("", controller.word.value)
    }

    @Test
    fun `typing right after a lift names the new path`() {
        trace(0, 1)
        controller.endTrace()
        controller.typeWord("go")

        assertEquals("GO", board().paths.single().word)
        assertEquals("GO", board().letters.take(2))
        assertEquals("GO", controller.word.value)
    }

    @Test
    fun `each lift selects the path it just made`() {
        trace(0, 1)
        controller.endTrace()
        trace(6, 7, 8)
        controller.endTrace()

        assertEquals(1, controller.selectedPath.value)
        controller.typeWord("cat")
        assertEquals(listOf(null, "CAT"), board().paths.map { it.word })
    }

    @Test
    fun `typed text longer than the path is kept whole and writes only the letters that fit`() {
        trace(0, 1)
        controller.endTrace()
        controller.typeWord("gold")

        assertEquals("GOLD", controller.word.value)
        assertEquals("GO", board().letters.take(2))
        assertEquals(StrandsDraft.BLANK, board().letters[2])
        assertNull(board().paths.single().word)
    }

    @Test
    fun `trimming overflow text back to the path length names it`() {
        trace(0, 1)
        controller.endTrace()
        controller.typeWord("gold")
        controller.typeWord("go")

        assertEquals("GO", board().paths.single().word)
    }

    @Test
    fun `with no selection typing is ignored and nothing waits for a lift`() {
        controller.typeWord("go")
        assertEquals("", controller.word.value)
        assertTrue(edits.isEmpty())

        trace(0, 1)
        controller.endTrace()

        assertEquals("", controller.word.value)
        assertEquals(StrandsDraft.BLANK.toString().repeat(2), board().letters.take(2))
        assertNull(board().paths.single().word)
    }

    @Test
    fun `dropping the selection clears the field`() {
        trace(0, 1)
        controller.endTrace()
        controller.typeWord("go")
        controller.selectPath(null)
        controller.typeWord("xy")

        assertEquals("", controller.word.value)
        assertEquals("GO", board().letters.take(2))
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
        trace(0, 1, 2, 3)
        controller.endTrace()
        controller.typeWord("c-a\u00E9f\u00C9e1")

        assertEquals("CAFE", controller.word.value)
    }

    @Test
    fun `a committed clue loses its line breaks`() {
        controller.setClue("a\nb\r\nc\rd")

        assertEquals("abcd", board().clue)
    }

    @Test
    fun `a clue typed key by key is one undo step`() {
        "Kitchen".indices.forEach { controller.setClue("Kitchen".take(it + 1)) }

        assertEquals("Kitchen", board().clue)
        assertEquals(1, controller.draft.value.state.history.done.size)
        val undone = checkNotNull(controller.draft.value.undo())
        assertEquals("", (undone.state.draft as StrandsDraft).clue)
    }

    @Test
    fun `a clue typed back to where it started leaves no step to undo or redo`() {
        controller.setClue("K")
        controller.setClue("")

        assertFalse(controller.draft.value.canUndo)
        assertFalse(controller.draft.value.canRedo)
    }

    @Test
    fun `a clue edit after a board edit is its own step`() {
        trace(0, 1)
        controller.endTrace()
        controller.setClue("K")
        controller.setClue("Ki")

        val undone = checkNotNull(controller.draft.value.undo()).state.draft as StrandsDraft
        assertEquals("", undone.clue)
        assertEquals(1, undone.paths.size)
    }

    @Test
    fun `a draft built from typed CAFE-acute and a pasted clue prints text that parses back`() {
        trace(0, 1, 2, 3)
        controller.endTrace()
        controller.typeWord("CAF\u00C9")
        controller.setClue("a\nb")

        val draft = board()
        val body = StrandsEditor.print(draft).lines().drop(1).joinToString("\n")
        assertEquals(NotationResult.Ok(draft), StrandsEditor.parse(body))
    }
}
