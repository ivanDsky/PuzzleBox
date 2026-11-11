package com.ivandsky.puzzlebox.gameui.board

/** One line of a board: a ranked PuzzleBox row, or an unranked ghost between ranked rows. */
sealed interface DailyBoardLine {
    data class Real(val row: DailyBoardRowModel) : DailyBoardLine

    data class Ghost(val row: GhostRowModel) : DailyBoardLine
}
