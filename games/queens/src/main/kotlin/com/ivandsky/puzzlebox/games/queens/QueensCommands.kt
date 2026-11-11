package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.Command

/**
 * The Queens BOARD state the commands edit: the standing queens (placement order, which
 * decides AUTO-mark provenance) and the MANUAL cross marks — the only marks that are
 * stored truth. AUTO marks are derived from these plus the Auto-cross setting by
 * [QueensSession], so a command never has to carry (or revert) its auto-fill side effects.
 * Manual marks may sit under a standing queen (the tap cycle's X → queen step keeps them).
 */
data class QueensBoard(
    val queens: List<Cell> = emptyList(),
    val manual: Set<Cell> = emptySet(),
)

/**
 * One reversible Queens action over a [QueensBoard] — one subtype per KIND of move, each
 * holding only the delta its own inverse needs. Commands are written against the board
 * they were built for; they are tolerant (set-like) rather than throwing on a mismatched
 * board, so a hand-edited or stale save can never crash an undo.
 */
sealed interface QueensCommand : Command<QueensBoard> {

    /** Tap-cycle X → queen (or any placement): append [cell] to the standing queens. */
    data class PlaceQueen(val cell: Cell) : QueensCommand {
        override fun apply(state: QueensBoard) = state.copy(queens = state.queens + cell)
        override fun revert(state: QueensBoard) = state.copy(queens = state.queens - cell)
    }

    /**
     * Tap-cycle queen → empty: the queen AND the manual mark hidden under it leave. The
     * delta is the queen's list [index] (provenance order is part of the board) and
     * whether a manual mark [hadMark] sat under it, so [revert] restores both.
     */
    data class RemoveQueen(val cell: Cell, val index: Int, val hadMark: Boolean) : QueensCommand {
        override fun apply(state: QueensBoard) =
            state.copy(queens = state.queens - cell, manual = state.manual - cell)

        override fun revert(state: QueensBoard): QueensBoard {
            val queens = state.queens - cell
            val restored = queens.toMutableList().apply { add(index.coerceIn(0, queens.size), cell) }
            return state.copy(queens = restored, manual = if (hadMark) state.manual + cell else state.manual)
        }
    }

    /** Manual cross on a queen-free cell: [wasMarked] is the state it replaced. */
    data class ToggleCross(val cell: Cell, val wasMarked: Boolean) : QueensCommand {
        override fun apply(state: QueensBoard) = state.copy(manual = state.manual.with(cell, !wasMarked))
        override fun revert(state: QueensBoard) = state.copy(manual = state.manual.with(cell, wasMarked))
    }

    /** One erase swipe: every manual cross in [cells] leaves as ONE undo unit. */
    data class EraseCrosses(val cells: Set<Cell>) : QueensCommand {
        override fun apply(state: QueensBoard) = state.copy(manual = state.manual - cells)
        override fun revert(state: QueensBoard) = state.copy(manual = state.manual + cells)
    }

    /** Clear: wipes everything; the removed [queens] and [manual] marks are the delta. */
    data class Clear(val queens: List<Cell>, val manual: Set<Cell>) : QueensCommand {
        override fun apply(state: QueensBoard) = QueensBoard()
        override fun revert(state: QueensBoard) = QueensBoard(queens, manual)
    }
}

private fun Set<Cell>.with(cell: Cell, marked: Boolean): Set<Cell> = if (marked) this + cell else this - cell
