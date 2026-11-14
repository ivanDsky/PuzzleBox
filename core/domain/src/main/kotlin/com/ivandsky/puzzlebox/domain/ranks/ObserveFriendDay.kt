package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
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
 * on. No flow at all with the hub's flag off: there is no setting to follow, the rank is Global and nothing is read.
 * Otherwise null is Global, and in Friends it is the [FriendDay] to rank one game day among: the friends (listened
 * to only then) and, with LinkedIn on, that day's connections, which are read from the phone only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFriendDay(
    private val preferences: UserPreferencesRepository,
    private val flags: ReleaseFlags,
    private val friendUids: ObserveFriendUids,
    private val linkedInDay: ObserveLinkedInDay,
) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<FriendDay?>? {
        if (!flags.isEnabled(ReleaseFlag.LeaderboardHub)) return null
        // A saved board outlives the flag that read it, so the flag is asked, not the data.
        val connections: Flow<LinkedInDayView?> = if (flags.isEnabled(ReleaseFlag.LinkedInAccount)) linkedInDay(game, day) else flowOf(null)
        val board = LeaderboardRef(game.value, day)
        return preferences.ranksFriendsDefault.distinctUntilChanged().flatMapLatest { friends ->
            if (friends) combine(friendUids(), connections) { uids, view -> FriendDay(board, uids, view) } else flowOf(null)
        }
    }
}
