package com.ivandsky.puzzlebox.play

/**
 * The Games screen's tabs; Dailies is the one a fresh screen starts on. The strip shows three of
 * them ([gamesTabs]): Custom takes Friends' place when the app hands Games a [CustomSlot], and
 * with a Community tab (the app's M10-1 tab set) Games keeps just Dailies and Duels.
 */
enum class GamesTab { Dailies, Friends, Duels, Custom }

private val FriendsPills = listOf(GamesTab.Dailies, GamesTab.Friends, GamesTab.Duels)
private val CustomPills = listOf(GamesTab.Dailies, GamesTab.Duels, GamesTab.Custom)
private val CommunityPills = listOf(GamesTab.Dailies, GamesTab.Duels)

/**
 * The tabs left to right: Dailies, Friends, Duels, or with a [custom] slot Dailies, Duels, Custom, or with a
 * [community] tab Dailies, Duels (the friend puzzles and Custom's list live on that tab).
 */
internal fun gamesTabs(custom: Boolean, community: Boolean = false): List<GamesTab> = when {
    community -> CommunityPills
    custom -> CustomPills
    else -> FriendsPills
}

/**
 * The tab a saved or requested tab shows as. Friends and Custom are the same place under the two
 * tab sets (friend puzzles live in both), so each stands in for the other when its own tab is
 * not there: a request for Friends opens Custom with a [custom] slot, and a Custom tab saved
 * before the slot went away shows Friends. With a [community] tab neither tab is there, so both
 * show Dailies.
 */
internal fun GamesTab.shownWith(custom: Boolean, community: Boolean = false): GamesTab = when {
    community -> if (this == GamesTab.Duels) this else GamesTab.Dailies
    else -> when (this) {
        GamesTab.Friends -> if (custom) GamesTab.Custom else this
        GamesTab.Custom -> if (custom) this else GamesTab.Friends
        GamesTab.Dailies, GamesTab.Duels -> this
    }
}
