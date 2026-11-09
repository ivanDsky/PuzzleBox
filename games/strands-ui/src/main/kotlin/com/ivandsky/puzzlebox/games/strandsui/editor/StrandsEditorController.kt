package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftCommand
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceInput
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Strands editor screen's live draft, trace and word text. [traceTo] grows the live
 * [trace] cell by cell over cells no path holds yet; [endTrace] lifts it: a trace of two or
 * more cells becomes one path, the [word] typed so far written onto the path's blank cells,
 * as one undoable step: an [StrandsDraftCommand.AddPath], or a [ReplaceDraft] when the word
 * also wrote letters. A tap on a traced cell selects its path ([selectedPath]); a drag from
 * the selected path's first or last cell shortens or extends it at that end ([StrandsEndGrip]),
 * one undoable step per drag; [removePath] drops it as one [StrandsDraftCommand.RemovePath];
 * [toggleSpangram] marks or unmarks one; [setClue] sets the theme clue. Every edit goes out
 * through `onEdit`; the VM hands replacement drafts back through [adopt].
 */
class StrandsEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))
    private val tracing = MutableStateFlow<List<Int>>(emptyList())
    private val typedWord = MutableStateFlow("")
    private val selected = MutableStateFlow<Int?>(null)
    private val shownOverlay = MutableStateFlow(EditorOverlay())

    /** The selected path's end drag, armed by a touch going down on an end. */
    private val grip = StrandsEndGrip()

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    override val overlay: StateFlow<EditorOverlay> = shownOverlay.asStateFlow()

    /** The cells of the trace under the finger, row-major, in trace order; never persisted. */
    val trace: StateFlow<List<Int>> = tracing.asStateFlow()

    /** The word text the next lift writes onto the path's blank cells. */
    val word: StateFlow<String> = typedWord.asStateFlow()

    /** The index of the selected path, null when none is; never persisted. */
    val selectedPath: StateFlow<Int?> = selected.asStateFlow()

    override fun adopt(draft: EditorDraft<*>) {
        current.value = typed(draft)
        tracing.value = emptyList()
        grip.drop()
        select(selected.value)
    }

    /**
     * Selects the path at [index], or none for null or an index no path has. The [word] then
     * shows the letters the path holds; dropping the selection clears it.
     */
    fun selectPath(index: Int?) {
        select(index)
        if (selected.value != null) tracing.value = emptyList()
    }

    private fun select(index: Int?) {
        val valid = index?.takeIf { it in current.value.state.draft.paths.indices }
        if (valid == null) {
            if (selected.value != null) typedWord.value = ""
        } else {
            typedWord.value = current.value.state.draft.spelled(valid)
        }
        selected.value = valid
    }

    /** Removes the path at [index] as one undoable step; a no-op for an index no path has. */
    fun removePath(index: Int) {
        val before = current.value.state.draft
        val path = before.paths.getOrNull(index) ?: return
        val next = current.value.apply(StrandsDraftCommand.RemovePath(index, path, before.spangram))
        current.value = next
        select(null)
        onEdit(next)
    }

    /**
     * Marks the path at [index] as the spangram, moving the mark off the path that held it, or
     * clears it when [index] already is the spangram; one undoable step, a no-op for an index no
     * path has.
     */
    fun toggleSpangram(index: Int) {
        val before = current.value.state.draft
        if (index !in before.paths.indices) return
        val target = if (before.spangram == index) StrandsDraft.NO_SPANGRAM else index
        val next = current.value.apply(StrandsDraftCommand.SetSpangram(target, before.spangram))
        current.value = next
        onEdit(next)
    }

    /**
     * Sets the theme clue; a no-op when [text] is the clue already. Consecutive clue edits are
     * one undoable step: a keystroke after a clue edit replaces that step, so one undo takes the
     * whole typing back, and typing back to the clue before it leaves no step at all. Line
     * breaks are dropped: the notation writes the clue on one line.
     */
    fun setClue(text: String) {
        val clue = text.filter { it != '\n' && it != '\r' }
        val before = current.value.state.draft.clue
        if (clue == before) return
        val last = current.value.state.history.done.lastOrNull() as? StrandsDraftCommand.SetClue
        val base = if (last != null) checkNotNull(current.value.undo()) else current.value
        val origin = last?.before ?: before
        val next = if (clue == origin) {
            // Typed back to where it started: the step goes, and with it the redo the undo left.
            EditorDraft(base.editor, DraftState(base.state.draft, CommandHistory(base.state.history.done)))
        } else {
            base.apply(StrandsDraftCommand.SetClue(clue, origin))
        }
        current.value = next
        onEdit(next)
    }

    override fun adoptOverlay(overlay: EditorOverlay) {
        shownOverlay.value = overlay
    }

    /**
     * Takes the word field's [text], letters A-Z only (any case, anything else dropped), upper
     * case. With a path selected the letters go onto its blank cells (and over the ones an
     * earlier typing wrote) as one undoable step; the path is named once the text is exactly as
     * long as the path. With none selected the text waits for the next lift.
     */
    fun typeWord(text: String) {
        val previous = typedWord.value
        val letters = text.filter { it in 'A'..'Z' || it in 'a'..'z' }.uppercase()
        typedWord.value = letters
        val index = selected.value ?: return
        val before = current.value.state.draft
        val path = before.paths[index]
        val board = before.letters.toCharArray()
        path.cells.forEachIndexed { i, cell ->
            val written = previous.getOrNull(i)
            val mine = board[cell] == StrandsDraft.BLANK || board[cell] == written
            when {
                i < letters.length && mine -> board[cell] = letters[i]
                i >= letters.length && board[cell] == written -> board[cell] = StrandsDraft.BLANK
            }
        }
        edit(index, path.cells, String(board), letters)
    }

    /**
     * Replaces the path at [index] with one over [cells] and the board [letters], when anything
     * changed.
     */
    private fun edit(index: Int, cells: List<Int>, letters: String, text: String) {
        val before = current.value.state.draft
        val path = before.paths[index]
        val spelled = cells.map { letters[it] }.joinToString("")
        val after = StrandsDraftPath(cells, spelled.takeIf { it == text })
        if (after == path && letters == before.letters) return
        val next = current.value.apply(StrandsDraftCommand.EditPath(index, path, after, before.letters, letters))
        current.value = next
        onEdit(next)
    }

    /**
     * Starts the trace at [cell], backs up over the previous cell, or appends an adjacent free
     * [cell]; else nothing changes.
     */
    fun traceTo(cell: Int) {
        val coord = coordOf(cell)
        val path = tracing.value.map(::coordOf)
        val step = if (path.isEmpty()) TraceInput.dragStart(coord) else TraceInput.dragTo(Trace(path), coord)
        show((step as? TraceStep.Updated)?.trace?.path ?: path)
    }

    /** Replaces the trace with [path]; refused whole when it touches a cell a path already holds. */
    internal fun show(path: List<Coord>) {
        val cells = path.map(::cellOf)
        if (cells.any { it in held() }) return
        tracing.value = cells
        if (cells.isNotEmpty()) select(null)
    }

    /**
     * One step of the play board's touch: a lone traced cell a path holds selects that path. A
     * step of an end drag ([StrandsEndGrip]) only shows its trace; its finger moves it ([fingerAt]).
     */
    internal fun step(step: TraceStep) {
        grip.take(step, current.value, selected.value)?.let {
            tracing.value = it
            return
        }
        when (step) {
            is TraceStep.Updated -> {
                val lone = step.trace.path.singleOrNull()?.let(::cellOf)
                val owner = if (lone == null) -1 else current.value.state.draft.paths.indexOfFirst { lone in it.cells }
                if (owner >= 0) selectPath(owner) else show(step.trace.path)
            }
            is TraceStep.Submit -> {
                show(step.path)
                endTrace()
            }
            TraceStep.Cleared -> clearTrace()
        }
    }

    /**
     * The board's dragging finger, in cell units; null at the lift. It moves a running end drag's
     * end, each move replacing the drag's one undoable step; the lift ends the drag.
     */
    internal fun fingerAt(at: Offset?) {
        val drag = grip.drag ?: return
        if (at == null) {
            grip.drop()
            tracing.value = emptyList()
            return
        }
        val next = drag.fingerAt(at) ?: return
        tracing.value = drag.trace
        current.value = next
        typedWord.value = next.state.draft.spelled(drag.index)
        onEdit(next)
    }

    /** A tap on [cell], as the play board would take it. */
    internal fun tapCell(cell: Int) = step(TraceInput.tap(Trace(tracing.value.map(::coordOf)), coordOf(cell)))

    /** Drops the trace without an edit. */
    internal fun clearTrace() {
        tracing.value = emptyList()
    }

    /** Lifts the finger: commits the trace as one path when it has two or more cells, then clears it. */
    fun endTrace() {
        val cells = tracing.value
        tracing.value = emptyList()
        if (cells.size < 2) return
        val before = current.value.state.draft
        val letters = before.letters.toCharArray()
        val text = typedWord.value
        cells.forEachIndexed { index, cell ->
            if (letters[cell] == StrandsDraft.BLANK && index < text.length) letters[cell] = text[index]
        }
        val spelled = cells.map { letters[it] }.joinToString("")
        val path = StrandsDraftPath(cells, spelled.takeIf { StrandsDraft.BLANK !in it })
        val next = if (String(letters) == before.letters) {
            current.value.apply(StrandsDraftCommand.AddPath(path))
        } else {
            current.value.apply(ReplaceDraft(before, before.copy(letters = String(letters), paths = before.paths + path)))
        }
        current.value = next
        selected.value = null
        typedWord.value = ""
        onEdit(next)
    }

    private fun held(): Set<Int> = current.value.state.draft.paths.flatMapTo(HashSet()) { it.cells }

    private fun coordOf(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)

    private companion object {
        /** The registry hands this controller Strands drafts only; anything else is a wiring bug. */
        fun typed(draft: EditorDraft<*>): EditorDraft<StrandsDraft> {
            require(draft.editor === StrandsEditor) { "not a Strands draft: ${draft.gameId.value}" }
            // Safe: the editor is StrandsEditor, so the draft and its history are Strands values.
            return draft as EditorDraft<StrandsDraft>
        }
    }
}
