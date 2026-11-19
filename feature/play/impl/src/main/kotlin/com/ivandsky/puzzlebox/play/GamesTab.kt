package com.ivandsky.puzzlebox.play

/**
 * The Games screen's tabs; Dailies is the one a fresh screen starts on. The friend puzzles and Custom's list live on
 * the app's Community tab (M10-1), so Games keeps Dailies, Duels and Train. Train comes last and is listed only while
 * the app hands in a [TrainSlot] ([gamesTabs]).
 */
enum class GamesTab { Dailies, Duels, Train }

/** The tabs the strip lists, in order: Train only when [trainShown], so a Games with no trainer has just Dailies and Duels. */
internal fun gamesTabs(trainShown: Boolean): List<GamesTab> = GamesTab.entries.filter { trainShown || it != GamesTab.Train }

/** This tab when [tabs] lists it, else Dailies: a saved Train selection outliving its slot (the flag went off) opens Dailies. */
internal fun GamesTab.orDailies(tabs: List<GamesTab>): GamesTab = if (this in tabs) this else GamesTab.Dailies

/** The one-line subtitle under the Games title for this tab. */
internal fun GamesTab.subtitle(): String = when (this) {
    GamesTab.Duels -> "Head-to-head, same puzzle"
    GamesTab.Dailies -> "Every puzzle, back to #1"
    GamesTab.Train -> "Learn the techniques, one step at a time"
}
