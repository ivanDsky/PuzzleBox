package com.ivandsky.puzzlebox.gameui.board

/** What a row's score column says. Its text is [boardRowValueText]. */
sealed interface BoardRowValue {
    /** One day's best: the time, and the hints that break a tie (or lead, on the Strands board). */
    data class Timed(val timeMs: Long, val hints: Int) : BoardRowValue

    /** A Week or Month board: the player's average over the days they played. */
    data class Average(val avgTimeMs: Long, val avgHints: Double) : BoardRowValue

    /** An Overall board: the sum of per-game points over [games] boards; [atLeast] when the sum is only a lower bound (A11). */
    data class Points(val total: Double, val games: Int, val atLeast: Boolean) : BoardRowValue
}
