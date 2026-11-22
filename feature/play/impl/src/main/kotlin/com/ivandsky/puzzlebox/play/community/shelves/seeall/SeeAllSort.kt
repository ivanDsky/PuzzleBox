package com.ivandsky.puzzlebox.play.community.shelves.seeall

/** The orders the sort menu can offer. Newest always exists; Most played only once the loaded puzzles carry solve counts. */
internal enum class SeeAllSort(val label: String) {
    MostPlayed("Most played"),
    Newest("Newest"),
}
