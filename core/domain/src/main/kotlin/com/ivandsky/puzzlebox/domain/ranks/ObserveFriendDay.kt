package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The scope a rank shown outside the hub answers (F20): the player's Friends/Global setting, the one the hub lands
 * on. Null is Global, and in Friends it is the [FriendDay] to rank one game day among: the friends (listened
 * to only then), that day's connections [linkedInDay], which are read from the phone only, and the viewer's own
 * [completions] of the day, which place them on a board they are past the page of. With [friendsPast] (backlog #1779)
 * the friends who placed past the page count too, as on the hub's boards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFriendDay(
    private val preferences: UserPreferencesRepository,
    private val friendUids: ObserveFriendUids,
    private val linkedInDay: ObserveLinkedInDay,
    private val completions: CompletionRepository,
    private val friendsPast: LoadFriendsPastPage? = null,
) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<FriendDay?> {
        val connections: Flow<LinkedInDayView?> = linkedInDay(game, day)
        val board = LeaderboardRef(game.value, day)
        val own = completions.observeRecords().map { records -> records.filter { LeaderboardRef.fromPuzzleRefKey(PuzzleRefKeys.encode(it.puzzleRef)) == board } }.distinctUntilChanged()
        // One memo a collection: the next one asks again, so it sees a friend who has placed past the page since.
        return flow {
            val memo = LoadFriendsPastPage.Memo()
            emitAll(
                preferences.ranksFriendsDefault.distinctUntilChanged().flatMapLatest { friends ->
                    if (friends) combine(friendUids(), connections, own) { uids, view, records -> FriendDay(board, uids, view, records, friendsPast, memo) } else flowOf(null)
                },
            )
        }
    }

    /**
     * What the composition root binds for the ranks outside the hub: the [reader] of the setting, or none in a graph
     * that builds no reader, when those ranks are Global. Koin cannot bind a null, so the root's answer travels in this.
     */
    class Binding(val reader: ObserveFriendDay?)
}
