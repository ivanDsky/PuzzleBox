package com.ivandsky.puzzlebox.play

/**
 * The Games screen's tabs; Dailies is the one a fresh screen starts on. The pills show three of
 * them ([gamesTabs]): Custom takes Friends' place when the app hands Games a [CustomSlot].
 */
enum class GamesTab { Dailies, Friends, Duels, Custom }

private val FriendsPills = listOf(GamesTab.Dailies, GamesTab.Friends, GamesTab.Duels)
private val CustomPills = listOf(GamesTab.Dailies, GamesTab.Duels, GamesTab.Custom)

/** The pills left to right: Dailies, Friends, Duels, or with a [custom] slot Dailies, Duels, Custom. */
internal fun gamesTabs(custom: Boolean): List<GamesTab> = if (custom) CustomPills else FriendsPills

/**
 * The tab a saved or requested tab shows as. Friends and Custom are the same place under the two
 * pill sets (friend puzzles live in both), so each stands in for the other when its own pill is
 * not there: a request for Friends opens Custom with a [custom] slot, and a Custom tab saved
 * before the slot went away shows Friends.
 */
internal fun GamesTab.shownWith(custom: Boolean): GamesTab = when (this) {
    GamesTab.Friends -> if (custom) GamesTab.Custom else this
    GamesTab.Custom -> if (custom) this else GamesTab.Friends
    GamesTab.Dailies, GamesTab.Duels -> this
}
