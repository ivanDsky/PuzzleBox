package com.ivandsky.puzzlebox.games.sudokuui

/** Where the keypad puts Notes, Erase and Clear relative to the 3-column digit block. */
enum class SudokuKeypadActions {
    /** One row of three under the digits. */
    Row,

    /** A fourth column beside the digits, so a cramped pane gives the row's height to the board. */
    Column,

    /** Only the notes toggle, as a fourth column the digits' height; Erase and Clear live elsewhere (TwoPane). */
    NotesColumn,

    /**
     * Under a play tuning: the digits take the full width and a 48dp strip above them holds the
     * small Notes toggle in its right corner, with Erase filling the strip when it is not in the
     * side column. Clear is in the shared control row.
     */
    Corner,
    ;

    /** True where the digit block spans the keypad's width (nothing sits beside it). */
    internal val digitsFillWidth: Boolean get() = this == Row || this == Corner
}
