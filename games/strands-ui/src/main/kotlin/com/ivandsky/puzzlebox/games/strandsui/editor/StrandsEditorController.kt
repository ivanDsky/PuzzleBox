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

    override fun adoptOverlay(overlay: EditorOverlay) = Unit

    /**
     * Takes the word field's [text], letters only, upper case. With a path selected the letters
     * go onto its blank cells (and over the ones an earlier typing wrote) as one undoable step;
     * the path is named once the text is exactly as long as the path. Longer text keeps its
     * overflow for [extend]. With none selected the text waits for the next lift.
     */
    fun typeWord(text: String) {
        val previous = typedWord.value
        val letters = text.filter(Char::isLetter).uppercase()
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
     * Grows the selected path by one cell at its end, into the first free neighbour (row-major)
     * that is blank or already holds the next letter of the [word]; a no-op when the word is
     * not longer than the path or no neighbour fits. One undoable step.
     */
    fun extend() {
        val index = selected.value ?: return
        val before = current.value.state.draft
        val cells = before.paths[index].cells
        val letter = typedWord.value.getOrNull(cells.size) ?: return
        val tail = cells.last()
        val taken = held()
        val next = (0 until StrandsDraft.ROWS * StrandsDraft.COLS).firstOrNull { cell ->
            cell !in taken &&
                kotlin.math.abs(cell / StrandsDraft.COLS - tail / StrandsDraft.COLS) <= 1 &&
                kotlin.math.abs(cell % StrandsDraft.COLS - tail % StrandsDraft.COLS) <= 1 &&
                before.letters[cell].let { it == StrandsDraft.BLANK || it == letter }
        } ?: return
        val board = before.letters.toCharArray().also { it[next] = letter }
        edit(index, cells + next, String(board), typedWord.value)
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
        val cells = path.map { it.row * StrandsDraft.COLS + it.col }
        if (cells.any { it in held() }) return
        tracing.value = cells
        if (cells.isNotEmpty()) select(null)
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

    private fun StrandsDraft.spelled(index: Int): String =
        paths[index].cells.map { letters[it] }.takeWhile { it != StrandsDraft.BLANK }.joinToString("")

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
