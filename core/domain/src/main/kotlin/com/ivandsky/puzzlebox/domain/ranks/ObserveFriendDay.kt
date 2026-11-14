package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * The scope a rank shown outside the hub answers (F20): the player's Friends/Global setting, the one the hub lands
 * on. Null is Global, and in Friends it is the [FriendDay] to rank one game day among: the friends (listened
 * to only then) and, given [linkedInDay], that day's connections, which are read from the phone only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFriendDay(
    private val preferences: UserPreferencesRepository,
    private val friendUids: ObserveFriendUids,
    private val linkedInDay: ObserveLinkedInDay?,
) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<FriendDay?> {
        val connections: Flow<LinkedInDayView?> = linkedInDay?.invoke(game, day) ?: flowOf(null)
        val board = LeaderboardRef(game.value, day)
        return preferences.ranksFriendsDefault.distinctUntilChanged().flatMapLatest { friends ->
            if (friends) combine(friendUids(), connections) { uids, view -> FriendDay(board, uids, view) } else flowOf(null)
        }
    }

    /**
     * What the composition root binds for the ranks outside the hub: the [reader] of the setting, or none with the
     * hub's flag off, when those ranks are Global. Koin cannot bind a null, so the root's answer travels in this.
     */
    class Binding(val reader: ObserveFriendDay?)
}
