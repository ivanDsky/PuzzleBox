package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The scope the hub lands on: Friends when the player's stored default says so and they have at least one friend,
 * else Global (an empty Friends board is a dead end). A fresh hub and a tab reselect land on [scope]; a restored
 * selection keeps its own. The inputs are read off the main thread: [scope] holds Global until they arrive, and
 * [stored] is the moment they have.
 */
internal class RanksLanding(preferences: UserPreferencesRepository, hasFriends: Flow<Boolean>, scope: CoroutineScope) {
    /** The landing choice, read once; suspends until the preference and the friends have loaded. */
    val stored: Flow<RankScope> = combine(preferences.ranksFriendsDefault, hasFriends) { friendsDefault, friends ->
        if (friendsDefault && friends) RankScope.Friends else RankScope.Global
    }

    /** Follows the setting and the friends, so a reselect after a change lands on the new default. */
    val scope: StateFlow<RankScope> = stored.stateIn(scope, SharingStarted.Eagerly, RankScope.Global)
}
