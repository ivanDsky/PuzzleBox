package com.ivandsky.puzzlebox.gameui

/**
 * The Clear seam (A14): wipe the player's marks back to the start as ONE undo unit. The
 * game owns what Clear removes (Zip its trace, Queens its queens and crosses, Tango its
 * placements). Clear is never disabled: on an empty or finished board [clear] does nothing
 * (no undo entry, no write). A game whose Clear lives elsewhere (Sudoku's keypad) passes
 * none and the action bar hides the button.
 */
fun interface PlayClear {
    fun clear()
}
