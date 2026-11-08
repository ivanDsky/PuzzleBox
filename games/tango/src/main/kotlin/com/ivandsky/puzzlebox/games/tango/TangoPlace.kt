package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.Command

/** The Tango BOARD state commands edit: the player overlay, row-major index -> value (givens live on the puzzle). */
typealias TangoPlacements = Map<Int, TangoCell>

/**
 * A single-cell Tango edit: cell [idx] goes from [previous] to [value], `null` meaning
 * "empty". It covers a fresh placement, a sun<->moon change and a clear alike, so undo
 * restores exactly the replaced value and nothing else. [previous] is what the cell held
 * when the command was built — the whole delta the inverse needs.
 */
data class TangoPlace(
    val idx: Int,
    val value: TangoCell?,
    val previous: TangoCell?,
) : TangoCommand {

    override fun apply(state: TangoPlacements): TangoPlacements = set(state, value)

    override fun revert(state: TangoPlacements): TangoPlacements = set(state, previous)

    private fun set(state: TangoPlacements, to: TangoCell?): TangoPlacements =
        if (to == null) state - idx else state + (idx to to)
}
