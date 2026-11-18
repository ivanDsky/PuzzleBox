package com.ivandsky.puzzlebox.games.strandsui.chrome

/** How [StrandsHintRow] lays the found counter and the Hint pill out. */
internal enum class HintRowPlacement {
    /**
     * Under the board (nav-rethink B5), as wide as the board: the shared row's Undo and Redo
     * half holds the counter, and the pill takes the Hint and Clear half, so it starts where
     * every other game's Hint does.
     */
    Slots,

    /** On a landscape tablet, in the side column: the counter over the pill in the shared column's width. */
    Column,
}
