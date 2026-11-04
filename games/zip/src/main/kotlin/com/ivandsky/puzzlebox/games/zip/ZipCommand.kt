package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.Command

/**
 * The Zip board state the player edits: the traced path, head last. Counters, timer and
 * the history live on [ZipSession]; commands only ever see the path.
 */
typealias ZipPath = List<Cell>

/**
 * One reversible Zip trace edit — a KIND of move per class, each holding only the delta
 * its inverse needs. Granularity is one session action: a drag that steps over several
 * cells is several [Extend]s (undo walks back cell by cell); a drag-back is ONE
 * [RetractTo] whatever its length, since the user did it in one gesture.
 *
 * Commands are applied only to paths the session already validated, but they stay total:
 * applied to a path they were not built against they leave it unchanged rather than throw
 * (a decoded history is checked for that in the codec, not trusted here).
 */
sealed interface ZipCommand : Command<ZipPath> {

    /** Append [cell] to the head (drag step, adjacent tap, or a hint's auto-placed step). */
    data class Extend(val cell: Cell) : ZipCommand {
        override fun apply(state: ZipPath): ZipPath = state + cell

        override fun revert(state: ZipPath): ZipPath =
            if (state.lastOrNull() == cell) state.dropLast(1) else state
    }

    /**
     * Trim the trace back to [target] (kept). [removedTail] is the cells that were cut,
     * oldest first — exactly what [revert] needs to grow the trace back.
     */
    data class RetractTo(val target: Cell, val removedTail: List<Cell>) : ZipCommand {
        override fun apply(state: ZipPath): ZipPath {
            val index = state.indexOf(target)
            return if (index < 0) state else state.subList(0, index + 1).toList()
        }

        override fun revert(state: ZipPath): ZipPath =
            if (state.lastOrNull() == target) state + removedTail else state
    }

    /** Empty the trace; [removedPath] is the whole trace that was cleared. */
    data class Reset(val removedPath: List<Cell>) : ZipCommand {
        override fun apply(state: ZipPath): ZipPath = emptyList()

        override fun revert(state: ZipPath): ZipPath = if (state.isEmpty()) removedPath else state
    }
}
