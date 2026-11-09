package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftCommand
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strandsui.TraceStep

/**
 * One drag on an end of the path at [index] of [base]: the path, anchored at its other end,
 * follows the finger as a trace does. The finger back over the cell before the [grabbed]
 * end pops that end (never below one cell); onto a free cell adjacent to the end, not on the
 * path and held by no other path, pushes it; anywhere else the end stays where it is.
 */
internal class StrandsEndDrag(val index: Int, private val base: EditorDraft<StrandsDraft>, grabbed: Int) {
    private val board = base.state.draft
    private val original = board.paths[index]
    private val fromStart = grabbed == original.cells.first() && original.cells.size > 1
    private val others = board.paths.filterIndexed { i, _ -> i != index }.flatMapTo(HashSet()) { it.cells }

    /** The working path, the grabbed end last: the live trace while the drag runs. */
    var trace: List<Int> = if (fromStart) original.cells.reversed() else original.cells
        private set

    /** The finger is over [cell]: pops or pushes the end; true when the path changed. */
    fun over(cell: Int): Boolean {
        val tail = trace.last()
        trace = when {
            trace.size >= 2 && cell == trace[trace.size - 2] -> trace.dropLast(1)
            cell !in trace && cell !in others && coordOf(tail).isAdjacentTo(coordOf(cell)) -> trace + cell
            else -> return false
        }
        return true
    }

    /**
     * What one step of the board's touch does: the finger over the trace's last cell moves the
     * end ([over]); null for the lift. A one-cell drag lifts with its trace as it is, as no move
     * does: a move onto any other cell sends two.
     */
    fun step(step: TraceStep): Boolean? {
        val cells = (step as? TraceStep.Updated)?.trace?.path?.map(::cellOf) ?: return null
        if (cells == trace && cells.size == 1) return null
        return cells.lastOrNull()?.let(::over) ?: false
    }

    /**
     * [base] with the path as dragged, as one [StrandsDraftCommand.EditPath]: cells in the path's
     * own reading order, the popped cells' letters dropped, named when no cell is blank. [base]
     * itself while the path has its cells from before the drag.
     */
    fun draft(): EditorDraft<StrandsDraft> {
        val cells = if (fromStart) trace.reversed() else trace
        if (cells == original.cells) return base
        val letters = board.letters.toCharArray()
        original.cells.forEach { if (it !in cells) letters[it] = StrandsDraft.BLANK }
        val spelled = cells.map { letters[it] }.joinToString("")
        val after = StrandsDraftPath(cells, spelled.takeIf { StrandsDraft.BLANK !in it })
        return base.apply(StrandsDraftCommand.EditPath(index, original, after, board.letters, String(letters)))
    }

    companion object {
        /** [cell] is the first or last cell of [path]: a drag from it moves that end. */
        fun grabs(path: StrandsDraftPath, cell: Int): Boolean = cell == path.cells.first() || cell == path.cells.last()

        private fun coordOf(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)
    }
}

/** The row-major index of [coord] on the editor's board. */
internal fun cellOf(coord: Coord): Int = coord.row * StrandsDraft.COLS + coord.col
