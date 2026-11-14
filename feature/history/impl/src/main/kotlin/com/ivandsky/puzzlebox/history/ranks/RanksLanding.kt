package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The scope the hub lands on: the player's stored default, Friends until they switch it in Settings. A fresh hub and
 * a tab reselect land on [scope]; a restored selection keeps its own. The stored value is read off the main thread:
 * [scope] holds Friends until it arrives, and [stored] is the moment it has.
 */
internal class RanksLanding(preferences: UserPreferencesRepository, scope: CoroutineScope) {
    /** The stored default, read once; suspends until the preference has loaded. */
    val stored: Flow<RankScope> = preferences.ranksFriendsDefault.map { if (it) RankScope.Friends else RankScope.Global }

    /** Follows the setting, so a reselect after a change lands on the new default. */
    val scope: StateFlow<RankScope> = stored.stateIn(scope, SharingStarted.Eagerly, RankScope.Friends)
}
