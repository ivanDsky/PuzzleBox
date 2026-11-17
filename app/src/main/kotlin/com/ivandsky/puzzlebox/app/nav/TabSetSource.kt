package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled

/**
 * The tabs the shell shows, in bar order, and the placement delta that goes with them. The
 * shell, the nav helpers and the fresh navigation state all take this one value.
 */
internal class TabSet(val destinations: List<TopLevelDestination>, val placement: PlacementDelta) {

    companion object {
        /** Today, Games, Ranks over the base table: Custom is a Games pill and the Studio is full-window. */
        val ThreeTabs = TabSet(topLevelDestinations, PlacementDelta.None)

        /** Today, Games, Community, Ranks: Custom's list is the Community tab, so Games has no Custom pill. */
        val CommunityTabs = TabSet(communityDestinations, communityPlacement)
    }
}

/**
 * The app graph's source of the [TabSet] (ADR-0027 §10): the only reader of the CommunityPuzzles flag. Off gives the
 * three tabs, on makes Community a tab as well. The flag is read once, when the graph builds this, because a live
 * navigator holds one stack per tab: a flip applies at the next launch, where a saved state is reconciled with the
 * new set.
 */
internal class TabSetSource(flags: ReleaseFlags) {
    val tabSet: TabSet = if (flags.isEnabled(ReleaseFlag.CommunityPuzzles)) TabSet.CommunityTabs else TabSet.ThreeTabs
}
