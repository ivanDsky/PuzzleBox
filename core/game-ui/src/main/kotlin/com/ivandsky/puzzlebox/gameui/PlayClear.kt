package com.ivandsky.puzzlebox.gameui

/**
 * The Clear seam (A14): wipe the player's marks back to the start as ONE undo unit. The
 * game owns what Clear removes (Zip its trace, Queens its queens and crosses). A game with
 * no Clear passes none and the action bar hides the button (Tango). [canClear] is false
 * when there is nothing to wipe or the board is done.
 */
fun interface PlayClear {
    fun clear()

    val canClear: Boolean get() = true
}
