package com.ivandsky.puzzlebox.games.strandsui.editor

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
 * also wrote letters. A tap on a traced cell selects its path ([selectedPath]); [removePath]
 * drops it as one [StrandsDraftCommand.RemovePath]. Every edit goes out through `onEdit`; the
 * VM hands replacement drafts back through [adopt].
 */
class StrandsEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))
    private val tracing = MutableStateFlow<List<Int>>(emptyList())
    private val typedWord = MutableStateFlow("")
    private val selected = MutableStateFlow<Int?>(null)

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    override val overlay: StateFlow<EditorOverlay> = MutableStateFlow(EditorOverlay()).asStateFlow()

    /** The cells of the trace under the finger, row-major, in trace order; never persisted. */
    val trace: StateFlow<List<Int>> = tracing.asStateFlow()

    /** The word text the next lift writes onto the path's blank cells. */
    val word: StateFlow<String> = typedWord.asStateFlow()

    /** The index of the selected path, null when none is; never persisted. */
    val selectedPath: StateFlow<Int?> = selected.asStateFlow()

    override fun adopt(draft: EditorDraft<*>) {
        current.value = typed(draft)
        tracing.value = emptyList()
        selected.value = selected.value?.takeIf { it in current.value.state.draft.paths.indices }
    }

    /** Selects the path at [index], or none for null or an index no path has. */
    fun selectPath(index: Int?) {
        selected.value = index?.takeIf { it in current.value.state.draft.paths.indices }
        if (selected.value != null) tracing.value = emptyList()
    }

    /** Removes the path at [index] as one undoable step; a no-op for an index no path has. */
    fun removePath(index: Int) {
        val before = current.value.state.draft
        val path = before.paths.getOrNull(index) ?: return
        val next = current.value.apply(StrandsDraftCommand.RemovePath(index, path, before.spangram))
        current.value = next
        selected.value = null
        onEdit(next)
    }

    override fun adoptOverlay(overlay: EditorOverlay) = Unit

    fun setWord(text: String) {
        typedWord.value = text.filter(Char::isLetter).uppercase()
    }

    /** Starts the trace at [cell], backs up over the previous cell, or appends an adjacent free [cell]; else nothing changes. */
    fun traceTo(cell: Int) {
        val coord = coordOf(cell)
        val path = tracing.value.map(::coordOf)
        val step = if (path.isEmpty()) TraceInput.dragStart(coord) else TraceInput.dragTo(Trace(path), coord)
        show((step as? TraceStep.Updated)?.trace?.path ?: path)
    }

    /** Replaces the trace with [path]; refused whole when it touches a cell a path already holds. */
    internal fun show(path: List<Coord>) {
        val cells = path.map { it.row * StrandsDraft.COLS + it.col }
        if (cells.any { it in held() }) return
        tracing.value = cells
        if (cells.isNotEmpty()) selected.value = null
    }

    /** One step of the play board's touch: a lone traced cell a path holds selects that path. */
    internal fun step(step: TraceStep) {
        when (step) {
            is TraceStep.Updated -> {
                val lone = step.trace.path.singleOrNull()?.let { it.row * StrandsDraft.COLS + it.col }
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
