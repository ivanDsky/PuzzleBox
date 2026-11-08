package com.ivandsky.puzzlebox.games.zipui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftCommand
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zip.editor.reversed
import com.ivandsky.puzzlebox.games.zip.editor.stroke
import com.ivandsky.puzzlebox.games.zip.editor.toggleMark
import com.ivandsky.puzzlebox.games.zip.editor.toggleWall
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.gameui.GridEdge
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Zip editor screen's live draft. A stroke ([beginStroke], [strokeTo]…, [endStroke]) draws
 * the author's path: it extends from either end over adjacent cells the walls leave open, and
 * drawing back onto the previous cell retracts by one. The board follows the stroke through
 * [shown]; lifting the finger commits it as one `PathEdit` step through [onEdit], as do
 * [reverse] and [clearPath]. The first and last cells of the path are always waypoints 1 and
 * last. [tapNumber] numbers a path cell or removes its number as one `ToggleMark` step; a tap
 * on an end, which cannot lose its number, sets [shaken]. [tapEdge] toggles a wall as one
 * `ToggleWall` step and [clearWalls] removes them all as one step; a wall across the path sets
 * [shaken] instead. The verdict card's witness choice arrives through [adoptOverlay] and the
 * board draws it from [overlay]. The VM hands replacement drafts back through [adopt].
 */
class ZipEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))
    private val showing = MutableStateFlow(current.value.state.draft)
    private val shownOverlay = MutableStateFlow(EditorOverlay())
    private val selected = MutableStateFlow(ZipEditorStep.Path)
    private val shaking = MutableStateFlow<Shake?>(null)
    private var anchor: Int? = null
    private var fromHead = false

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    /** The board as drawn: the draft with the stroke in flight applied. */
    val shown: StateFlow<ZipDraft> = showing.asStateFlow()

    /** The step whose tools the row shows; the stroke draws in [ZipEditorStep.Path] only. */
    val step: StateFlow<ZipEditorStep> = selected.asStateFlow()

    /**
     * The cell the last refused [tapNumber] or [tapEdge] landed on; each refusal is a new
     * value.
     */
    internal val shaken: StateFlow<Shake?> = shaking.asStateFlow()

    /** A refused tap on [cell]; a fresh instance per tap so a repeat tap re-triggers. */
    internal class Shake(val cell: Int)

    override val overlay: StateFlow<EditorOverlay> = shownOverlay.asStateFlow()

    override fun adopt(draft: EditorDraft<*>) {
        anchor = null
        fromHead = false
        current.value = typed(draft)
        showing.value = current.value.state.draft
    }

    override fun adoptOverlay(overlay: EditorOverlay) {
        shownOverlay.value = overlay
    }

    fun select(step: ZipEditorStep) {
        selected.value = step
    }

    /**
     * The finger lands on [cell]; nothing changes until it moves to another cell. Landing on the
     * first cell of a longer path carries the path on from its head.
     */
    fun beginStroke(cell: Int) {
        val path = showing.value.path
        anchor = cell
        fromHead = path.size > 1 && cell == path.first()
    }

    /** The finger reaches [cell]: a step the path allows extends or retracts it, any other cell is ignored. */
    fun strokeTo(cell: Int) {
        val from = anchor
        if (from != null && cell == from) return
        val board = showing.value
        val drawn = if (fromHead) board.path.asReversed() else board.path
        val path = if (from == null) drawn else resumed(drawn, from)
        val next = advance(board, path, cell)
        anchor = null
        showing.value = withPath(current.value.state.draft, if (fromHead) next.asReversed() else next)
    }

    /** The finger lifts: a changed path becomes one undoable edit. */
    fun endStroke() {
        anchor = null
        fromHead = false
        val path = showing.value.path
        if (path == current.value.state.draft.path) return
        commit(current.value.state.draft.stroke(path))
    }

    /** The path run backwards, its numbers following it, as one edit. */
    fun reverse() {
        val board = current.value.state.draft
        if (board.path.size > 1) commit(board.reversed())
    }

    /** Empties the path as one edit. */
    fun clearPath() {
        val board = current.value.state.draft
        if (board.path.isNotEmpty()) commit(board.stroke(emptyList()))
    }

    /** Numbers [cell], or removes its number; off the path it does nothing, on an end it shakes. */
    fun tapNumber(cell: Int) {
        val board = current.value.state.draft
        val toggle = board.toggleMark(cell)
        when {
            toggle != null -> commit(toggle)
            cell in board.path -> shaking.value = Shake(cell)
        }
    }

    /**
     * Toggles the wall on [edge], one `ToggleWall` step; a wall that would cut between two
     * consecutive path cells is refused and sets [shaken] on the edge's cell.
     */
    fun tapEdge(edge: GridEdge) {
        val cell = edge.cell.row * current.value.state.draft.n + edge.cell.col
        val direction = if (edge.side == GridEdge.Side.Right) WallDirection.RIGHT else WallDirection.DOWN
        val toggle = current.value.state.draft.toggleWall(Wall(Cell(cell), direction))
        if (toggle != null) commit(toggle) else shaking.value = Shake(cell)
    }

    /** Removes every wall as one edit. */
    fun clearWalls() {
        val walls = current.value.state.draft.walls
        if (walls.isNotEmpty()) commit(ZipDraftCommand.ClearWalls(walls))
    }

    private fun commit(edit: ZipDraftCommand) {
        val next = current.value.apply(edit)
        current.value = next
        showing.value = next.state.draft
        onEdit(next)
    }

    private fun advance(board: ZipDraft, path: List<Int>, cell: Int): List<Int> {
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

        /** [board] drawn along [path]: ends are waypoints 1 and last, marks on the path keep order. */
        fun withPath(board: ZipDraft, path: List<Int>): ZipDraft = board.stroke(path).apply(board)
    }
}
