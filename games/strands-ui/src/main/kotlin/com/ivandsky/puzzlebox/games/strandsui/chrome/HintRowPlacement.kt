package com.ivandsky.puzzlebox.games.strandsui.chrome

/** How [StrandsHintRow] lays the found counter and the Hint pill out. */
internal enum class HintRowPlacement {
    /** NYT's row, no play tuning: the counter at the left, the pill at the right. */
    Legacy,

    /**
     * Under a play tuning (nav-rethink B5), as wide as the board: the shared row's Undo and Redo
     * half holds the counter, and the pill takes the Hint and Clear half, so it starts where
     * every other game's Hint does.
     */
    Slots,

    /** Under a play tuning on a landscape tablet set to a side column: the counter over the pill in the shared column's width. */
    Column,
}
