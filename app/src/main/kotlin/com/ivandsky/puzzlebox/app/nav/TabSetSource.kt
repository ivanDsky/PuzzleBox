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
        /** Today, Games, Ranks, Create over the base table. */
        val FourTabs = TabSet(topLevelDestinations, PlacementDelta.None)

        /** Today, Games, Ranks: making puzzles lives in Games, so Create is not a tab. */
        val CustomInGames = TabSet(customInGamesDestinations, customInGamesPlacement)
    }
}

/**
 * The app graph's source of the [TabSet] (ADR-0027 §10): the only reader of the CustomInGames
 * flag. The flag is read once, when the graph builds this, because a live navigator holds one
 * stack per tab: a flip applies at the next launch, where a saved state is reconciled with the
 * new set.
 */
internal class TabSetSource(flags: ReleaseFlags) {
    val tabSet: TabSet = if (flags.isEnabled(ReleaseFlag.CustomInGames)) TabSet.CustomInGames else TabSet.FourTabs
}
