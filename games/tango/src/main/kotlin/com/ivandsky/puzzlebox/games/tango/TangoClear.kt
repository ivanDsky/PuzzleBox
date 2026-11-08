package com.ivandsky.puzzlebox.games.tango

/**
 * Clear: every player placement wiped as ONE undo unit. It keeps what it wiped
 * ([wiped], row-major index -> value) so revert restores exactly the pre-clear overlay;
 * givens never enter the overlay, so they survive by construction.
 */
data class TangoClear(val wiped: TangoPlacements) : TangoCommand {

    override fun apply(state: TangoPlacements): TangoPlacements = emptyMap()

    override fun revert(state: TangoPlacements): TangoPlacements = wiped
}
