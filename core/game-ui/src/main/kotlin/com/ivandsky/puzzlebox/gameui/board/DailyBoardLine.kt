package com.ivandsky.puzzlebox.gameui.board

/**
 * One line of a board, with its [place] in the one combined ranking: a PuzzleBox row (which keeps its own
 * [DailyBoardRowModel.rank]), or a LinkedIn ghost, unranked (null place) when its time is hidden.
 */
sealed interface DailyBoardLine {
    data class Real(val row: DailyBoardRowModel, val place: Int) : DailyBoardLine

    data class Ghost(val row: GhostRowModel, val place: Int?) : DailyBoardLine
}
