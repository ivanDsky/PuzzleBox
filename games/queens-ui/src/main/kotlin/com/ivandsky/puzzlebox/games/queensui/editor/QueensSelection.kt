package com.ivandsky.puzzlebox.games.queensui.editor

/**
 * The cells the author has picked before choosing a ring colour (spec S3). Changing the
 * selection is not an edit: it never touches the draft or its history.
 */
internal data class QueensSelection(val cells: Set<Int> = emptySet()) {
    /** Toggles [cell]: a selected cell leaves the selection, any other joins it. */
    fun tap(cell: Int): QueensSelection = QueensSelection(if (cell in cells) cells - cell else cells + cell)
}
