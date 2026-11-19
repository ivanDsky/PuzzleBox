package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Games
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The scope and games the hub lands on. The scope is Friends when the player's stored default says so and they have at
 * least one friend, else Global (an empty Friends board is a dead end); the games are LinkedIn games when the player
 * saved that default, with [gamesFilter] (GamesFilter) on, else All games. A fresh hub and a tab reselect land on
 * [scope] and [games]; a restored selection keeps its own. The inputs are read off the main thread: [scope] and [games]
 * hold Global and All games until they arrive, and [stored] and [storedGames] are the moment they have.
 */
internal class RanksLanding(preferences: UserPreferencesRepository, hasFriends: Flow<Boolean>, gamesFilter: Boolean, scope: CoroutineScope) {
    /** The landing choice, read once; suspends until the preference and the friends have loaded. */
    val stored: Flow<RankScope> = combine(preferences.ranksFriendsDefault, hasFriends) { friendsDefault, friends ->
        if (friendsDefault && friends) RankScope.Friends else RankScope.Global
    }

    /** The landing games, read once like [stored]; All games for good while the filter is off. */
    val storedGames: Flow<Games> = if (gamesFilter) {
        preferences.ranksLinkedInDefault.map { if (it) Games.LinkedIn else Games.All }
    } else {
        flowOf(Games.All)
    }

    /** Follows the setting and the friends, so a reselect after a change lands on the new default. */
    val scope: StateFlow<RankScope> = stored.stateIn(scope, SharingStarted.Eagerly, RankScope.Global)

    /** Follows the setting the same way, for a reselect. */
    val games: StateFlow<Games> = storedGames.stateIn(scope, SharingStarted.Eagerly, Games.All)

    /** A fresh hub's first pick: [selection] on the stored scope and games ([boardsOf] lists what a games pick leaves), unless the player picked meanwhile. */
    suspend fun land(selection: MutableStateFlow<RanksSelection>, boardsOf: (Games) -> List<RanksBoard>) {
        val initial = selection.value
        val scope = stored.first()
        val games = storedGames.first()
        selection.update { if (it == initial) it.setScope(scope).land(games, boardsOf(games)) else it }
    }
}
