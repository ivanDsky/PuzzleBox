package com.ivandsky.puzzlebox.games.sudokuui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Sudoku editor screen's live draft and selected cell. A [tap] selects a cell (the same
 * cell again clears it); a [digit] then places that digit on it, or erases with 0, as one
 * undoable `ReplaceDraft` step sent out through [onEdit]. The selection survives edits so
 * several digits can be tried on one cell. The VM hands replacement drafts back through [adopt].
 */
class SudokuEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))
    private val picked = MutableStateFlow<Int?>(null)
    private val shownOverlay = MutableStateFlow(EditorOverlay())

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    override val overlay: StateFlow<EditorOverlay> = shownOverlay.asStateFlow()

    /** The selected cell's row-major index; null when none. */
    val selected: StateFlow<Int?> = picked.asStateFlow()

    override fun adopt(draft: EditorDraft<*>) {
        val next = typed(draft)
        // A resize renumbers every cell, so the old selection no longer means anything.
        if (next.state.draft.size != current.value.state.draft.size) picked.value = null
        current.value = next
    }

    override fun adoptOverlay(overlay: EditorOverlay) {
        shownOverlay.value = overlay
    }

    /** Selects [cell], or clears the selection when it is the one already selected. Not an edit. */
    fun tap(cell: Int) {
        val board = current.value.state.draft
        require(cell in board.givens.indices) { "no cell $cell on a ${board.size}×${board.size} board" }
        picked.value = if (picked.value == cell) null else cell
    }

    /**
     * Puts [d] (0 erases) on the selected cell as one `ReplaceDraft` step. Nothing selected, or
     * the cell already holding [d]: no edit.
     */
    fun digit(d: Int) {
        val cell = picked.value ?: return
        val before = current.value.state.draft
        require(d in 0..before.size) { "digit $d outside 0..${before.size}" }
        if (before.givens[cell] == d) return
        val after = before.copy(givens = before.givens.toMutableList().also { it[cell] = d })
        val next = current.value.apply(ReplaceDraft(before, after))
        current.value = next
        onEdit(next)
    }

    private companion object {
        /** The registry hands this controller Sudoku drafts only; anything else is a wiring bug. */
        fun typed(draft: EditorDraft<*>): EditorDraft<SudokuDraft> {
            require(draft.editor is SudokuEditor) { "not a Sudoku draft: ${draft.gameId.value}" }
            // Safe: the editor is a SudokuEditor, so the draft and its history are Sudoku values.
            return draft as EditorDraft<SudokuDraft>
        }
    }
}
