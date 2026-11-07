package com.ivandsky.puzzlebox.games.strands.editor

/**
 * An editable Strands board: [ROWS] × [COLS] [letters] in row-major order, [BLANK] for an
 * empty cell, the theme [clue], the traced [paths] (empty on share and import boards) and
 * [spangram], the index of the spangram in [paths] or [NO_SPANGRAM].
 */
data class StrandsDraft(
    val letters: String,
    val clue: String,
    val paths: List<StrandsDraftPath>,
    val spangram: Int,
) {
    companion object {
        const val ROWS = 8
        const val COLS = 6
        const val BLANK = '.'
        const val NO_SPANGRAM = -1
    }
}
