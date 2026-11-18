package com.ivandsky.puzzlebox.play

/**
 * The Games screen's tabs; Dailies is the one a fresh screen starts on. The friend puzzles and Custom's list live on
 * the app's Community tab (M10-1), so Games keeps just Dailies and Duels.
 */
enum class GamesTab { Dailies, Duels }

/** The one-line subtitle under the Games title for this tab. */
internal fun GamesTab.subtitle(): String = when (this) {
    GamesTab.Duels -> "Head-to-head, same puzzle"
    GamesTab.Dailies -> "Every puzzle, back to #1"
}
