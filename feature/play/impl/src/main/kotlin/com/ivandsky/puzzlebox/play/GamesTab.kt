package com.ivandsky.puzzlebox.play

/**
 * The Games screen's tabs; Dailies is the one a fresh screen starts on. The strip shows [gamesTabs]: Custom takes
 * the third place, and with a Community tab (the app's M10-1 tab set) Games keeps just Dailies and Duels.
 */
enum class GamesTab { Dailies, Duels, Custom }

private val DefaultPills = listOf(GamesTab.Dailies, GamesTab.Duels, GamesTab.Custom)
private val CommunityPills = listOf(GamesTab.Dailies, GamesTab.Duels)

/**
 * The tabs left to right: Dailies, Duels, Custom, or with a [community] tab Dailies, Duels (the friend puzzles
 * and Custom's list live on that tab).
 */
internal fun gamesTabs(community: Boolean = false): List<GamesTab> = if (community) CommunityPills else DefaultPills

/**
 * The tab a saved or requested tab shows as. With a [community] tab Custom is not there, so it shows Dailies.
 */
internal fun GamesTab.shownWith(community: Boolean = false): GamesTab =
    if (community && this == GamesTab.Custom) GamesTab.Dailies else this
