package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * A window's boards as the Friends scope ranks them (F18, F21), with the LinkedIn connections of their days counted
 * among the friends, read from the phone's own saved days: no sync, and no Firestore read beyond [friendsPast]'s. The
 * boards are built by the domain's one Friends pipeline ([amongFriends]), the same the entry points' place uses;
 * without LinkedIn ([linkedIn] null) no connection is counted. Both Friends Overall ([ObserveFriendsOverall]) and the
 * Ranks hub run their window's read through [among], then [invoke].
 *
 * A board is read to its page (A23), so a viewer who placed past it has no entry on it. Their own [completions] place
 * them there ([beyondPageEntries], A11), so the Friends boards of those days still rank them among their friends. A
 * friend who placed past it has none either: with [friendsPast] their entries are read by id ([LoadFriendsPastPage])
 * and counted; without it (null) they are not, as before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFriendBoards(
    private val observeFriendUids: ObserveFriendUids,
    private val identity: IdentityService,
    private val completions: CompletionRepository,
    private val linkedIn: ObserveLinkedInDayView?,
    private val friendsPast: LoadFriendsPastPage? = null,
) {

    /**
     * A window's [boards] as read, with the [players] Friends ranks on them, the viewer's entry on the boards they
     * placed [own] past the page of and the entries the friends who placed [past] the page of a board have on it.
     */
    data class Among(
        val boards: RankWindowBoards,
        val players: Set<String>,
        val own: Map<LeaderboardRef, LeaderboardEntry>,
        val past: Map<LeaderboardRef, List<LeaderboardEntry>> = emptyMap(),
    )

    /** An answer of [among] before the friends past a page are read for it. */
    private class Heard(val window: RankWindow, val among: Among, val viewerUid: String?)

    /**
     * A window's [boards] (null while none has answered), each with the players Friends ranks on it: the viewer's
     * friends and the viewer themselves, followed as they change. With [friendsPast], an answer waits for the reads of
     * the friends a full page leaves out, so no place is drawn first without them and then with them; a newer answer
     * does not wait behind them, and each collection reads a board for a friend once.
     */
    fun among(boards: Flow<Pair<RankWindow, RankWindowBoards>?>): Flow<Pair<RankWindow, Among>?> {
        val heard = combine(boards, observeFriendUids(), identity.current, completions.observeRecords()) { answered, friends, me, records ->
            answered?.let { (window, read) -> Heard(window, Among(read, friends + setOfNotNull(me?.uid), ownBeyondPage(read, me, records)), me?.uid) }
        }
        val loadPast = friendsPast ?: return heard.map { it?.let { answer -> answer.window to answer.among } }
        return flow {
            val memo = LoadFriendsPastPage.Memo()
            emitAll(
                heard.conflate().map { answer ->
                    answer?.let { answer.window to answer.among.copy(past = loadPast(memo, answer.among.boards, answer.among.players, answer.viewerUid)) }
                },
            )
        }
    }

    private fun ownBeyondPage(read: RankWindowBoards, me: Identity?, records: List<CompletionRecord>): Map<LeaderboardRef, LeaderboardEntry> =
        if (read is RankWindowBoards.Boards && me != null) beyondPageEntries(read.boards.filterIsInstance<BoardLoad.Loaded>(), me, records) else emptyMap()

    /**
     * [boards] (null while they have not answered) among their players, with their connections counted. The boards
     * wait for the phone's days of exactly their own days, so no place is drawn first without a connection and then
     * with it; the days are listened to once per set of days, not once per board answer. [boards] is collected twice,
     * so the caller shares it.
     */
    operator fun invoke(boards: Flow<Among?>): Flow<CountedBoards?> {
        if (linkedIn == null) return boards.map { it?.counted(emptyMap(), viewer = null) }
        val days = boards.map { it.refs() }.distinctUntilChanged().flatMapLatest { refs -> daysOf(linkedIn, refs).map { refs to it } }
        return combine(boards, days, identity.current) { read, (refs, days), me ->
            when {
                read == null -> Answer(null)
                read.refs() != refs -> null
                else -> Answer(read.counted(days, me))
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

    private fun Among.counted(days: Map<LeaderboardRef, LinkedInDayView>, viewer: Identity?): CountedBoards {
        val plain = boards.amongFriends(players, viewerEntries = own, friendEntries = past)
        return CountedBoards(plain, if (days.isEmpty()) plain else boards.amongFriends(players, days, viewer, own, past), days)
    }

    private fun Among?.refs(): List<LeaderboardRef> = (this?.boards as? RankWindowBoards.Boards)?.boards.orEmpty().map { it.board }

    /** One answer of [invoke], null inside while the boards have none: a wrapper, so a skipped step is not mistaken for it. */
    private class Answer(val counted: CountedBoards?)
}
