package com.ivandsky.puzzlebox.app.widget

/** Where the player stands on a solved game's board as of the widget's last draw, or why the widget has no place to show. */
sealed interface WidgetRank {

    /** 1-based [position] among [count] entries, as the Results screen counts them (Friends or Global, by the player's setting). */
    data class Ranked(val position: Int, val count: Int) : WidgetRank

    /** No rank to show: the game is not solved, the board is loading, unavailable or has no entry of the player's, or the read failed or ran out of time. */
    data object Missing : WidgetRank

    /** The player is signed out. */
    data object SignedOut : WidgetRank
}
