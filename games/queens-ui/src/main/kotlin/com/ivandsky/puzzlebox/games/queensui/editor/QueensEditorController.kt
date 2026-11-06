package com.ivandsky.puzzlebox.games.queensui.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Queens editor screen's live draft. A tap paints one cell with the next region
 * colour ([paint]); each paint is one undoable step on the draft's history and goes out
 * through [onEdit]. The VM hands replacement drafts back through [adopt].
 */
class QueensEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    override fun adopt(draft: EditorDraft<*>) {
        current.value = typed(draft)
    }

    /** Cycles [cell] to the next region: unpainted → 0, then 0, 1, …, size − 1, 0. */
    internal fun paint(cell: Int) {
        val board = current.value.state.draft
        val before = board.regions[cell]
        val after = if (before == QueensDraft.UNPAINTED) 0 else (before + 1) % board.size
        val next = current.value.apply(PaintCell(cell, before, after))
        current.value = next
        onEdit(next)
    }

    /** One cell's region change. Ticket 02 replaces it with the shared QueensDraftCommand. */
    private class PaintCell(val cell: Int, val before: Int, val after: Int) : Command<QueensDraft> {
        override fun apply(state: QueensDraft): QueensDraft = state.painted(after)
        override fun revert(state: QueensDraft): QueensDraft = state.painted(before)
        private fun QueensDraft.painted(region: Int): QueensDraft =
            copy(regions = regions.toMutableList().also { it[cell] = region })
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
