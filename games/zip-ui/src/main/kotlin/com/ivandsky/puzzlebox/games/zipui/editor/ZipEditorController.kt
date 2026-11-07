package com.ivandsky.puzzlebox.games.zipui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Zip editor screen's live draft. A stroke ([beginStroke], [strokeTo]…, [endStroke]) draws
 * the author's path: it extends over adjacent cells the walls leave open, and drawing back onto
 * the previous cell retracts by one. The board follows the stroke through [shown]; lifting
 * the finger commits it as one `ReplaceDraft` step through [onEdit]. The first and last cells of the
 * path are always waypoints 1 and last. The VM hands replacement drafts back through [adopt].
 */
class ZipEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))
    private val showing = MutableStateFlow(current.value.state.draft)
    private val shownOverlay = MutableStateFlow(EditorOverlay())
    private var anchor: Int? = null

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    /** The board as drawn: the draft with the stroke in flight applied. */
    val shown: StateFlow<ZipDraft> = showing.asStateFlow()

    override val overlay: StateFlow<EditorOverlay> = shownOverlay.asStateFlow()

    override fun adopt(draft: EditorDraft<*>) {
        anchor = null
        current.value = typed(draft)
        showing.value = current.value.state.draft
    }

    override fun adoptOverlay(overlay: EditorOverlay) {
        shownOverlay.value = overlay
    }

    /** The finger lands on [cell]; nothing changes until it moves to another cell. */
    fun beginStroke(cell: Int) {
        anchor = cell
    }

    /** The finger reaches [cell]: a step the path allows extends or retracts it, any other cell is ignored. */
    fun strokeTo(cell: Int) {
        val from = anchor
        if (from != null && cell == from) return
        val board = showing.value
        val path = if (from == null) board.path else resumed(board.path, from)
        anchor = null
        showing.value = withPath(current.value.state.draft, step(board, path, cell))
    }

    /** The finger lifts: a changed path becomes one undoable edit. */
    fun endStroke() {
        anchor = null
        val before = current.value.state.draft
        val after = showing.value
        if (after.path == before.path) return
        val next = current.value.apply(ReplaceDraft(before, after))
        current.value = next
        onEdit(next)
    }

    private fun step(board: ZipDraft, path: List<Int>, cell: Int): List<Int> {
        val last = path.lastOrNull() ?: return listOf(cell)
        return when {
            cell == last -> path
            path.size >= 2 && cell == path[path.lastIndex - 1] -> path.dropLast(1)
            cell in path -> path
            canExtend(board, last, cell) -> path + cell
            else -> path
        }
    }

    private fun canExtend(board: ZipDraft, from: Int, to: Int): Boolean {
        val a = Cell(from)
        val b = Cell(to)
        return ZipRules.isAdjacent(a, b, board.n) && !ZipBoard(board.n, board.walls, emptyList()).blocked(a, b)
    }

    private companion object {
        /** The registry hands this controller Zip drafts only; anything else is a wiring bug. */
        fun typed(draft: EditorDraft<*>): EditorDraft<ZipDraft> {
            require(draft.editor === ZipEditor) { "not a Zip draft: ${draft.gameId.value}" }
            // Safe: the editor is ZipEditor, so the draft and its history are Zip values.
            return draft as EditorDraft<ZipDraft>
        }

        /** Where a stroke landing on [from] carries on: the tail, the path cut back to [from], or a new path at [from]. */
        fun resumed(path: List<Int>, from: Int): List<Int> = when {
            path.isEmpty() -> listOf(from)
            from == path.last() -> path
            from in path -> path.take(path.indexOf(from) + 1)
            else -> listOf(from)
        }

        /** [board] drawn along [path]: the ends are waypoints 1 and last; marks between the old ends that are still on the path keep their order. */
        fun withPath(board: ZipDraft, path: List<Int>): ZipDraft {
            val ends = setOf(path.first(), path.last())
            val interior = if (board.path.isEmpty()) emptyList() else board.waypoints.drop(1).dropLast(1)
            val marks = interior.filter { it in path && it !in ends }.sortedBy(path::indexOf)
            val labelled = listOf(path.first()) + marks + listOfNotNull(path.last().takeIf { path.size > 1 })
            return board.copy(path = path, waypoints = labelled)
        }
    }
}
