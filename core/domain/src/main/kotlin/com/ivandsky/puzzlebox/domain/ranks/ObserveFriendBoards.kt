package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * A window's boards as the Friends scope ranks them (F18, F21), with the LinkedIn connections of their days counted
 * among the friends, read from the phone's own saved days: no Firestore read and no sync. The boards are built by the
 * domain's one Friends pipeline ([amongFriends]), the same the entry points' place uses; without LinkedIn ([linkedIn]
 * null) no connection is counted. Both Friends Overall ([ObserveFriendsOverall]) and the Ranks hub run their window's
 * read through [among], then [invoke].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFriendBoards(
    private val observeFriendUids: ObserveFriendUids,
    private val identity: IdentityService,
    private val linkedIn: ObserveLinkedInDayView?,
) {

    /** A window's [boards] as read, with the [players] Friends ranks on them. */
    data class Among(val boards: RankWindowBoards, val players: Set<String>)

    /**
     * A window's [boards] (null while none has answered), each with the players Friends ranks on it: the viewer's
     * friends and the viewer themselves, followed as they change.
     */
    fun among(boards: Flow<Pair<RankWindow, RankWindowBoards>?>): Flow<Pair<RankWindow, Among>?> =
        combine(boards, observeFriendUids(), identity.current) { answered, friends, me ->
            answered?.let { (window, read) -> window to Among(read, friends + setOfNotNull(me?.uid)) }
        }

    /**
     * [boards] (null while they have not answered) among their players, with their connections counted. The boards
     * wait for the phone's days of exactly their own days, so no place is drawn first without a connection and then
     * with it; the days are listened to once per set of days, not once per board answer. [boards] is collected twice,
     * so the caller shares it.
     */
    operator fun invoke(boards: Flow<Among?>): Flow<CountedBoards?> {
        if (linkedIn == null) return boards.map { it?.counted(emptyMap(), viewerUid = null) }
        val days = boards.map { it.refs() }.distinctUntilChanged().flatMapLatest { refs -> daysOf(linkedIn, refs).map { refs to it } }
        return combine(boards, days, identity.current) { read, (refs, days), me ->
            when {
                read == null -> Answer(null)
                read.refs() != refs -> null
                else -> Answer(read.counted(days, me?.uid))
            }
        }.mapNotNull { it }.map { it.counted }
    }

    /** One range a game from its first day to its last, so a month costs as many listeners as a day does. */
    private fun daysOf(linkedIn: ObserveLinkedInDayView, refs: List<LeaderboardRef>): Flow<Map<LeaderboardRef, LinkedInDayView>> {
        // Combining nothing never emits.
        if (refs.isEmpty()) return flowOf(emptyMap())
        val ranges = refs.groupBy({ it.gameId }, { it.day }).map { (game, days) ->
            linkedIn.observedRange(GameId(game), days.min(), days.max()).map { game to it }
        }
        return combine(ranges) { games ->
            val byGame = games.toMap()
            refs.associateWith { byGame.getValue(it.gameId).getValue(it.day) }
        }
    }

    private fun Among.counted(days: Map<LeaderboardRef, LinkedInDayView>, viewerUid: String?): CountedBoards {
        val plain = boards.amongFriends(players)
        return CountedBoards(plain, if (days.isEmpty()) plain else boards.amongFriends(players, days, viewerUid), days)
    }

    private fun Among?.refs(): List<LeaderboardRef> = (this?.boards as? RankWindowBoards.Boards)?.boards.orEmpty().map { it.board }

    /** One answer of [invoke], null inside while the boards have none: a wrapper, so a skipped step is not mistaken for it. */
    private class Answer(val counted: CountedBoards?)
}
