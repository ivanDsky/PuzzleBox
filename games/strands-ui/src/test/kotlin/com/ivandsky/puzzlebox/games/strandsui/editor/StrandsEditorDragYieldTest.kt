package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftCommand
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A running end drag stops when another edit lands: that edit stays, and the next move changes nothing. */
class StrandsEditorDragYieldTest {

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) { edits += it }

    private fun coord(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)

    /** A selected "STAR" path over cells 0-3 whose last end is being dragged back to cell 2. */
    private fun draggingEnd(beforeDrag: () -> Unit = {}) {
        listOf(0, 1, 2, 3).forEach(controller::traceTo)
        controller.endTrace()
        controller.selectPath(0)
        controller.typeWord("star")
        beforeDrag()
        controller.step(TraceInput.dragStart(coord(3)))
        controller.step(TraceInput.dragTo(Trace(listOf(coord(3))), coord(2)))
    }

    private fun moveDrag() = controller.fingerAt(Offset(1.5f, 0.5f))

    private fun assertYielded(command: Class<*>, edit: () -> Unit) {
        draggingEnd()
        edit()
        val after = controller.draft.value
        assertTrue(command.isInstance(after.state.history.done.last()))
        assertTrue(after.undo() != null)
        val editsAfter = edits.size
        moveDrag()
        assertSame(after, controller.draft.value)
        assertEquals(editsAfter, edits.size)
        assertTrue(controller.trace.value.isEmpty())
    }

    @Test
    fun `removing a path mid drag stays removed`() = assertYielded(StrandsDraftCommand.RemovePath::class.java) { controller.removePath(0) }

    @Test
    fun `toggling the spangram mid drag stays toggled`() = assertYielded(StrandsDraftCommand.SetSpangram::class.java) {
        controller.toggleSpangram(0)
        assertEquals(0, (controller.draft.value.state.draft as StrandsDraft).spangram)
    }

    @Test
    fun `setting the clue mid drag keeps the clue`() = assertYielded(StrandsDraftCommand.SetClue::class.java) {
        controller.setClue("fruit")
        assertEquals("fruit", (controller.draft.value.state.draft as StrandsDraft).clue)
    }

    @Test
    fun `typing a word mid drag keeps the word`() = assertYielded(StrandsDraftCommand.EditPath::class.java) {
        controller.typeWord("sxar")
        assertEquals("SXAR", controller.word.value)
        assertEquals(listOf(0, 1, 2, 3), (controller.draft.value.state.draft as StrandsDraft).paths.single().cells)
    }

    @Test
    fun `selecting another path mid drag keeps the draft and the new selection`() {
        draggingEnd {
            listOf(6, 7).forEach(controller::traceTo)
            controller.endTrace()
            controller.selectPath(0)
        }
        val after = controller.draft.value
        controller.selectPath(1)
        moveDrag()
        assertSame(after, controller.draft.value)
        assertEquals(1, controller.selectedPath.value)
        assertEquals((after.state.draft as StrandsDraft).spelled(1), controller.word.value)
    }
}
