package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import kotlinx.coroutines.flow.flowOf

/** Defaults for the Tune sheet in a test that builds the route table: an unset tier, the hub landing on Friends and All games, writes ignored. */
internal fun testTuneDefaults() = RanksTuneDefaults(
    ownTier = flowOf(OwnTier.Unset),
    onSelectTier = {},
    friendsDefault = flowOf(true),
    onSelectFriendsDefault = {},
    linkedInDefault = flowOf(false),
    onSelectLinkedInDefault = {},
)
