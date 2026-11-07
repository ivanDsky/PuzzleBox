package com.ivandsky.puzzlebox.games.sudokuui

/** Where the keypad puts Notes, Erase and Clear relative to the 3-column digit block. */
enum class SudokuKeypadActions {
    /** One row of three under the digits. */
    Row,

    /** A fourth column beside the digits, so a cramped pane gives the row's height to the board. */
    Column,

    /** Only the notes toggle, as a fourth column the digits' height; Erase and Clear live elsewhere (TwoPane). */
    NotesColumn,
}
