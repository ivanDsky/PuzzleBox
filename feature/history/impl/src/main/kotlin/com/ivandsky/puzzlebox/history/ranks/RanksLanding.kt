package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * The scope the hub lands on (F1): the player's stored default, Friends until they switch it in Settings. A fresh hub and
 * a tab reselect land on [scope]; a restored selection (A8) keeps its own. The first value is read before the hub's first
 * frame, so a Global default never flashes Friends; the store is warm by then (the app root has read its theme from it).
 */
internal class RanksLanding(preferences: UserPreferencesRepository, scope: CoroutineScope) {
    private val stored = preferences.ranksFriendsDefault.map { if (it) RankScope.Friends else RankScope.Global }

    /** Follows the setting, so a reselect after a change lands on the new default. */
    val scope: StateFlow<RankScope> = stored.stateIn(scope, SharingStarted.Eagerly, runBlocking { stored.first() })
}
