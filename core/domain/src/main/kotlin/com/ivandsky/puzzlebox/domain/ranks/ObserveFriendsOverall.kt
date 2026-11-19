package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

/**
 * Overall among friends (F18) over a [RankWindow] of games, with the LinkedIn connections of its days counted (F21):
 * the window's boards ([ObserveRankWindow]) among the viewer and their friends ([ObserveFriendBoards]), ranked by
 * points ([ObserveOverall]). The Ranks hub runs the same stages over its one shared window read, so both give the
 * same points for the same window. Cold: each collection reads the window once, for its own lifetime.
 */
class ObserveFriendsOverall(
    private val observeRankWindow: ObserveRankWindow,
    private val observeFriendUids: ObserveFriendUids,
    private val identity: IdentityService,
    private val friendBoards: ObserveFriendBoards,
    private val observeOverall: ObserveOverall,
) {
    operator fun invoke(window: RankWindow, games: List<GameId>): Flow<OverallState> = channelFlow {
        // The friend boards collect their input twice (its days and its places): one window read serves both.
        val among = combine(observeRankWindow(window, games), observeFriendUids(), identity.current) { boards, friends, me ->
            ObserveFriendBoards.Among.of(boards, friends, me)
        }.shareIn(this, SharingStarted.Eagerly, replay = 1)
        observeOverall(friendBoards(among, identity.current).mapNotNull { it?.counted }).collect { send(it) }
    }
}
