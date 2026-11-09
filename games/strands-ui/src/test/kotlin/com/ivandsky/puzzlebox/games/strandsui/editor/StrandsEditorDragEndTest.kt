package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceInput
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dragging the selected path's first or last cell, through the steps the play board's touch
 * sends ([drag] stands in for its drag session): back over the path shortens it, onto a free
 * neighbour extends it, one drag is one undo step.
 */
class StrandsEditorDragEndTest {

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) { edits += it }

    private fun board() = controller.draft.value.state.draft as StrandsDraft

    private fun coord(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)

    private fun live(on: StrandsEditorController = controller) = Trace(on.trace.value.map(::coord))

    /** Commits a path over [cells] by tracing them and lifting. */
    private fun path(vararg cells: Int) {
        cells.forEach(controller::traceTo)
        controller.endTrace()
    }

    /** "STAR" over the top row's first four cells, selected. */
    private fun selectedStar() {
        path(0, 1, 2, 3)
        controller.selectPath(0)
        controller.typeWord("star")
    }

    /**
     * A touch on [from] whose finger goes to the centre of each of [over] in turn, then lifts,
     * in [on]'s order of the play board's calls: the drag session's steps (the first join starts
     * the drag at [from], later ones a [TraceInput.dragTo] on the live trace), then the finger;
     * at the lift the finger's null, then the session's last step. No join is a tap.
     */
    private fun drag(from: Int, vararg over: Int, on: StrandsEditorController = controller) {
        over.forEachIndexed { index, cell ->
            if (index == 0) {
                on.step(TraceInput.dragStart(coord(from)))
                on.step(TraceInput.dragTo(Trace(listOf(coord(from))), coord(cell)))
            } else {
                on.step(TraceInput.dragTo(live(on), coord(cell)))
            }
            on.fingerAt(Offset(cell % StrandsDraft.COLS + 0.5f, cell / StrandsDraft.COLS + 0.5f))
        }
        if (over.isNotEmpty()) on.fingerAt(null)
        on.step(if (over.isEmpty()) TraceInput.tap(live(on), coord(from)) else TraceInput.dragEnd(live(on)))
    }

    @Test
    fun `dragging the last cell back two cells shortens the path and drops their letters`() {
        selectedStar()
        drag(3, 2, 1)

        assertEquals(listOf(0, 1), board().paths.single().cells)
        assertEquals("ST....", board().letters.take(6))
        assertEquals("ST", board().paths.single().word)
        assertEquals("ST", controller.word.value)
        assertEquals(0, controller.selectedPath.value)
        assertTrue(controller.trace.value.isEmpty())
    }

    @Test
    fun `dragging the last cell onto a chosen free neighbour extends the path that way`() {
        selectedStar()
        // Row-major the first free neighbour of (0, 3) is (0, 4); the finger picks (1, 4).
        drag(3, 10)

        assertEquals(listOf(0, 1, 2, 3, 10), board().paths.single().cells)
        assertEquals(StrandsDraft.BLANK, board().letters[10])
        assertNull(board().paths.single().word)
    }

    @Test
    fun `extending onto a lettered free cell keeps its letter and the word follows the cells`() {
        val letters = "STARE".padEnd(StrandsDraft.ROWS * StrandsDraft.COLS, StrandsDraft.BLANK)
        val draft = StrandsDraft(letters, "", listOf(StrandsDraftPath(listOf(0, 1, 2, 3), "STAR")), StrandsDraft.NO_SPANGRAM)
        val controller = StrandsEditorController(EditorDraft(StrandsEditor, DraftState(draft))) {}
        controller.selectPath(0)
        drag(3, 4, on = controller)

        val after = controller.draft.value.state.draft as StrandsDraft
        assertEquals(StrandsDraftPath(listOf(0, 1, 2, 3, 4), "STARE"), after.paths.single())
        assertEquals("STARE", controller.word.value)
    }

    @Test
    fun `a cell popped in one drag comes back blank in the next`() {
        selectedStar()
        drag(3, 2)
        drag(2, 3)

        assertEquals(listOf(0, 1, 2, 3), board().paths.single().cells)
        assertEquals(StrandsDraft.BLANK, board().letters[3])
        assertNull(board().paths.single().word)
    }

    @Test
    fun `dragging the first cell moves the start and the word still reads from it`() {
        selectedStar()
        drag(0, 1)
        assertEquals(listOf(1, 2, 3), board().paths.single().cells)
        assertEquals("TAR", board().paths.single().word)

        drag(1, 6)
        assertEquals(listOf(6, 1, 2, 3), board().paths.single().cells)
        assertNull(board().paths.single().word)
    }

    @Test
    fun `a cell another path holds stops the end and the drag goes on from it`() {
        path(4, 10)
        path(0, 1, 2, 3)
        controller.selectPath(1)
        drag(3, 4, 9, 15)

        assertEquals(listOf(4, 10), board().paths[0].cells)
        assertEquals(listOf(0, 1, 2, 3, 9, 15), board().paths[1].cells)
    }

    /** A walk that took a held cell for joined reroutes: its pop and push are not the finger's. */
    @Test
    fun `the walk's own pops and pushes never move the end, only the finger does`() {
        selectedStar()
        val count = edits.size
        controller.step(TraceInput.dragStart(coord(3)))
        controller.step(TraceInput.dragTo(Trace(listOf(coord(3))), coord(9)))
        controller.step(TraceInput.dragTo(live(), coord(2)))
        controller.step(TraceStep.Updated(Trace(listOf(0, 1, 2, 8).map(::coord))))
        assertEquals(listOf(0, 1, 2, 3), controller.trace.value)
        controller.fingerAt(null)
        controller.step(TraceInput.dragEnd(live()))

        assertEquals(listOf(0, 1, 2, 3), board().paths.single().cells)
        assertEquals("STAR", board().letters.take(4))
        assertEquals(count, edits.size)
    }

    @Test
    fun `a finger off every cell centre leaves the end where it is`() {
        selectedStar()
        controller.step(TraceInput.dragStart(coord(3)))
        controller.step(TraceInput.dragTo(Trace(listOf(coord(3))), coord(9)))
        // The corner (0, 3) shares with (1, 3), (1, 4) and (0, 4): on no cell's circle.
        controller.fingerAt(Offset(4f, 1f))
        assertEquals(listOf(0, 1, 2, 3), controller.trace.value)
        controller.fingerAt(Offset(4.5f, 1.5f))
        assertEquals(listOf(0, 1, 2, 3, 10), controller.trace.value)
    }

    @Test
    fun `shortening stops at one cell and never removes the path`() {
        path(0, 1, 2)
        controller.selectPath(0)
        drag(2, 1, 0)

        assertEquals(listOf(listOf(0)), board().paths.map { it.cells })
        assertTrue(controller.trace.value.isEmpty())
        drag(0, 1)
        assertEquals(listOf(listOf(0, 1)), board().paths.map { it.cells })
    }

    @Test
    fun `one drag is one undo step that restores the exact path and letters`() {
        selectedStar()
        val before = board()
        val steps = controller.draft.value.state.history.done.size
        drag(3, 2, 1, 7, 13)

        assertEquals(listOf(0, 1, 7, 13), board().paths.single().cells)
        assertEquals(steps + 1, controller.draft.value.state.history.done.size)
        assertEquals(before, controller.draft.value.undo()!!.state.draft)
    }

    @Test
    fun `a drag that ends where it began leaves no step`() {
        selectedStar()
        val steps = controller.draft.value.state.history.done.size
        drag(3, 2, 3)

        assertEquals(listOf(0, 1, 2, 3), board().paths.single().cells)
        assertEquals("STAR", board().paths.single().word)
        assertEquals(steps, controller.draft.value.state.history.done.size)
    }

    @Test
    fun `a drag from a middle cell edits nothing`() {
        selectedStar()
        val count = edits.size
        drag(1, 7, 13)

        assertEquals(listOf(0, 1, 2, 3), board().paths.single().cells)
        assertEquals(count, edits.size)
    }

    @Test
    fun `a drag from a free cell starts a new trace and lets the selection go`() {
        selectedStar()
        drag(24, 25, 26)

        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(24, 25, 26)), board().paths.map { it.cells })
        assertNull(controller.selectedPath.value)
    }

    @Test
    fun `a tap on an end keeps the selection and starts no drag`() {
        selectedStar()
        val count = edits.size
        drag(3)

        assertEquals(0, controller.selectedPath.value)
        assertTrue(controller.trace.value.isEmpty())
        assertEquals(count, edits.size)
    }

    @Test
    fun `an end of a path not selected is no handle`() {
        path(0, 1, 2, 3)
        drag(3, 9)

        assertEquals(listOf(0, 1, 2, 3), board().paths.single().cells)
    }
}
