package com.ivandsky.puzzlebox.games.queensui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.editor.paint
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Queens editor screen's live draft and selection (spec S3, select then colour). A tap
 * toggles a cell in the [selection] ([QueensSelection]); [selectAllOf] and [selectArea] grow
 * it; a ring [pick] paints every selected cell as one undoable step, sends the new draft out
 * through [onEdit], and clears the selection. The VM hands replacement drafts back through [adopt].
 */
class QueensEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))
    private val selected = MutableStateFlow<Set<Int>>(emptySet())

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    /** The selected cells, row-major indices; the ring is open while it is non-empty. */
    val selection: StateFlow<Set<Int>> = selected.asStateFlow()

    override fun adopt(draft: EditorDraft<*>) {
        val next = typed(draft)
        // A resize renumbers every cell, so the old selection no longer means anything.
        if (next.state.draft.size != current.value.state.draft.size) cancel()
        current.value = next
    }

    /** Toggles [cell] in the selection. Not an edit. */
    fun tap(cell: Int) {
        selected.value = QueensSelection(selected.value).tap(cell).cells
    }

    /** Grows the selection by every cell painted [region] (`QueensDraft.UNPAINTED` too). Not an edit. */
    fun selectAllOf(region: Int) {
        selected.value = QueensSelection(selected.value).allOf(current.value.state.draft, region).cells
    }

    /** Grows the selection by the rectangle with corners [from] and [to]. Not an edit. */
    fun selectArea(from: Int, to: Int) {
        selected.value = QueensSelection(selected.value).area(current.value.state.draft.size, from, to).cells
    }

    /**
     * Paints the selection with [region] (`QueensDraft.UNPAINTED` unpaints) as one
     * `QueensDraftCommand.Paint` step, then clears the selection. Nothing selected: no edit.
     */
    fun pick(region: Int) {
        val chosen = selected.value
        if (chosen.isEmpty()) return
        val board = current.value.state.draft
        require(region == QueensDraft.UNPAINTED || region in 0 until board.size) { "no region $region on a ${board.size}×${board.size} board" }
        val next = current.value.apply(board.paint(chosen, region))
        current.value = next
        cancel()
        onEdit(next)
    }

    /** Drops the selection without an edit. */
    fun cancel() {
        selected.value = emptySet()
    }

    private companion object {
        /** The registry hands this controller Queens drafts only; anything else is a wiring bug. */
        fun typed(draft: EditorDraft<*>): EditorDraft<QueensDraft> {
            require(draft.editor === QueensEditor) { "not a Queens draft: ${draft.gameId.value}" }
            // Safe: the editor is QueensEditor, so the draft and its history are Queens values.
            return draft as EditorDraft<QueensDraft>
        }
    }
}
