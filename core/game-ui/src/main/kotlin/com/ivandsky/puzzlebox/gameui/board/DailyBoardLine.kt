package com.ivandsky.puzzlebox.gameui.board

/**
 * One line of a board, with its [place] in the one combined ranking: a PuzzleBox row (which keeps its own
 * [DailyBoardRowModel.rank]), or a LinkedIn ghost, unranked (null place) when its time is hidden.
 */
sealed interface DailyBoardLine {
    data class Real(val row: DailyBoardRowModel, val place: Int) : DailyBoardLine

    data class Ghost(val row: GhostRowModel, val place: Int?) : DailyBoardLine
}

/**
 * True when a line's place may differ from the PuzzleBox rank its row keeps: a LinkedIn ghost is on the board, or a
 * row shows another time than its PuzzleBox one and so sits elsewhere. Such a board names each row's PuzzleBox rank.
 */
fun List<DailyBoardLine>.placesDifferFromPuzzleBox(): Boolean =
    any { it is DailyBoardLine.Ghost || (it is DailyBoardLine.Real && it.place != it.row.rank) }
