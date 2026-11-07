package com.ivandsky.puzzlebox.games.queensui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.editor.QueensAnswerFit
import com.ivandsky.puzzlebox.games.queens.editor.QueensCrowns
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.editor.paint
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Queens editor screen's live draft, [mode] and selection. In [QueensEditorMode.Crown] a
 * tap places or removes an answer crown ([QueensCrowns.toggle]) as one undoable step, and a
 * refused tap (a crossed cell) is announced on [rejected] instead. In [QueensEditorMode.Paint]
 * (select, then colour) a tap toggles a cell in the [selection] ([QueensSelection]);
 * [selectAllOf] and [selectArea] grow it; a ring [pick] paints every selected cell as one
 * undoable step and clears the selection. In either mode a [stroke] (a drag out from a
 * coloured cell) paints that cell's colour along the drag. Every edit goes out through
 * [onEdit]; the VM hands replacement drafts back through [adopt]. The verdict card's witness
 * choice arrives through [adoptOverlay] and the board draws it from [overlay].
 */
class QueensEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))
    private val selected = MutableStateFlow<Set<Int>>(emptySet())
    private val currentMode = MutableStateFlow(openingMode(current.value.state.draft))
    private val refused = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val shownOverlay = MutableStateFlow(EditorOverlay())

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    /** The selected cells, row-major indices; the ring is open while it is non-empty. */
    val selection: StateFlow<Set<Int>> = selected.asStateFlow()

    /** What a [tap] does; not persisted. */
    val mode: StateFlow<QueensEditorMode> = currentMode.asStateFlow()

    /** The cell of each refused crown tap; the board plays the Reject jolt on it. */
    val rejected: SharedFlow<Int> = refused.asSharedFlow()

    override val overlay: StateFlow<EditorOverlay> = shownOverlay.asStateFlow()

    override fun adopt(draft: EditorDraft<*>) {
        val next = typed(draft)
        // A resize renumbers every cell, so the old selection no longer means anything.
        if (next.state.draft.size != current.value.state.draft.size) cancel()
        current.value = next
    }

    override fun adoptOverlay(overlay: EditorOverlay) {
        shownOverlay.value = overlay
    }

    /**
     * With answer crowns on a 2+ solutions [verdict], the witness the crowns do not
     * follow, drawn on the board as the ghost crowns; otherwise the default, so the diff ring alone shows.
     */
    override fun openingOverlay(verdict: Verdict): EditorOverlay {
        val multiple = verdict as? Verdict.Multiple ?: return EditorOverlay()
        val fit = QueensAnswerFit.of(current.value.state.draft, multiple) as? QueensAnswerFit.Ambiguous ?: return EditorOverlay()
        val other = if (fit.authorIsA) EditorOverlay.Witness.B else EditorOverlay.Witness.A
        return EditorOverlay(other, showOnBoard = true)
    }

    /** Switches what a [tap] does and clears the selection; the mode already on changes nothing. Not an edit. */
    fun setMode(mode: QueensEditorMode) {
        if (mode == currentMode.value) return
        cancel()
        currentMode.value = mode
    }

    /** Paint mode with exactly [cells] selected, in their order (a fix's Select). Not an edit. */
    internal fun select(cells: Set<Int>) {
        currentMode.value = QueensEditorMode.Paint
        selected.value = cells
    }

    /** Grows the selection by [cells] (a selecting drag); a cell already selected stays. Not an edit. */
    internal fun extend(cells: Collection<Int>) {
        selected.value = selected.value + cells
    }

    /** Crown: toggles an answer crown on [cell] (an edit) or emits it on [rejected]. Paint: toggles [cell] in the selection. */
    fun tap(cell: Int) {
        when (currentMode.value) {
            QueensEditorMode.Crown -> crown(cell)
            QueensEditorMode.Paint -> selected.value = QueensSelection(selected.value).tap(cell).cells
        }
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

    /**
     * A drag out from [from] (either mode): paints [from]'s region over every cell of [cells]
     * that holds no crown, as one `QueensDraftCommand.Paint` step. No edit when [from] is
     * UNPAINTED or nothing would change. The selection is left as it is.
     */
    fun stroke(from: Int, cells: List<Int>) {
        val board = current.value.state.draft
        val region = board.regions[from]
        if (region == QueensDraft.UNPAINTED) return
        val target = cells.filterTo(LinkedHashSet()) { it !in board.crowns && board.regions[it] != region }
        if (target.isEmpty()) return
        val next = current.value.apply(board.paint(target, region))
        current.value = next
        onEdit(next)
    }

    /** Drops the selection without an edit. */
    fun cancel() {
        selected.value = emptySet()
    }

    private fun crown(cell: Int) {
        val command = QueensCrowns.toggle(current.value.state.draft, cell)
        if (command == null) {
            refused.tryEmit(cell)
            return
        }
        val next = current.value.apply(command)
        current.value = next
        onEdit(next)
    }

    private companion object {
        /** The registry hands this controller Queens drafts only; anything else is a wiring bug. */
        fun typed(draft: EditorDraft<*>): EditorDraft<QueensDraft> {
            require(draft.editor === QueensEditor) { "not a Queens draft: ${draft.gameId.value}" }
            // Safe: the editor is QueensEditor, so the draft and its history are Queens values.
            return draft as EditorDraft<QueensDraft>
        }

        /** Crown for a new draft (nothing painted outside the crowns), else Paint. */
        fun openingMode(board: QueensDraft): QueensEditorMode {
            val painted = board.regions.indices.any { it !in board.crowns && board.regions[it] != QueensDraft.UNPAINTED }
            return if (painted) QueensEditorMode.Paint else QueensEditorMode.Crown
        }
    }
}
