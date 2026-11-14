package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.amongFriends
import com.ivandsky.puzzlebox.domain.ranks.connectionUid
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
 * The LinkedIn ghosts of the hub's boards (A15), read from what the phone already holds: owner-only local data, so no
 * Firestore read and no sync. In Friends scope every day of the window has its connections counted (F21), each taking
 * a place among the friends (F18); Global, and a hub built without LinkedIn ([linkedIn] null), get none and touch nothing.
 * Friends boards are built here by the domain's one pipeline ([amongFriends]), the same the entry points' place uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BoardGhosts(private val linkedIn: ObserveLinkedInDayView?) {

    /**
     * A scope's boards as it ranks them without connections ([plain]) and with the connections of their days ranked
     * among them ([counted]), the saved [days] being what was merged. Without connections the two are the same boards.
     */
    data class Counted(val plain: RankWindowBoards, val counted: RankWindowBoards, val days: Map<LeaderboardRef, LinkedInDayView>) {

        /** What [game]'s one day adds to its Day board; a Day window holds one day a game. */
        fun ghostInput(game: GameId): BoardGhostInput =
            days.entries.firstOrNull { it.key.gameId == game.value }?.value?.toGhostInput() ?: BoardGhostInput.None

        /** Every counted connection's cached photo, by the uid its row is ranked under. */
        fun photos(): Map<String, String?> = days.values
            .flatMap { (it.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty() }
            .associate { connectionUid(it.memberRef) to it.photoPath }
    }

    /**
     * [boards] (null while they have not answered) as [scope] ranks them, with their connections counted. In Friends
     * the boards wait for the phone's days of exactly their own days, so no place is drawn first without a connection
     * and then with it; the days are listened to once per set of days, not once per board answer.
     */
    fun counted(scope: RankScope, boards: Flow<RanksScopedBoards.Scoped?>, viewer: Flow<Identity?>): Flow<Counted?> {
        if (linkedIn == null || scope != RankScope.Friends) return boards.map { it?.toCounted(emptyMap(), viewerUid = null) }
        val days = boards.map { it.refs() }.distinctUntilChanged().flatMapLatest { refs -> daysOf(linkedIn, refs).map { refs to it } }
        return combine(boards, days, viewer) { read, (refs, days), me ->
            when {
                read == null -> Answer(null)
                read.refs() != refs -> null
                else -> Answer(read.toCounted(days, me?.uid))
            }
        }.mapNotNull { it }.map { it.counted }
    }

    private fun daysOf(linkedIn: ObserveLinkedInDayView, refs: List<LeaderboardRef>): Flow<Map<LeaderboardRef, LinkedInDayView>> {
        // Combining nothing never emits.
        if (refs.isEmpty()) return flowOf(emptyMap())
        return combine(refs.map { linkedIn.observed(ObserveLinkedInDayView.Target(GameId(it.gameId), it.day)) }) { views -> refs.zip(views).toMap() }
    }

    private fun RanksScopedBoards.Scoped.toCounted(days: Map<LeaderboardRef, LinkedInDayView>, viewerUid: String?): Counted {
        if (among == null) return Counted(boards, boards, emptyMap())
        val plain = boards.amongFriends(among)
        return Counted(plain, if (days.isEmpty()) plain else boards.amongFriends(among, days, viewerUid), days)
    }

    private fun RanksScopedBoards.Scoped?.refs(): List<LeaderboardRef> = (this?.boards as? RankWindowBoards.Boards)?.boards.orEmpty().map { it.board }

    /** One answer of [counted], null inside while the boards have none: a wrapper, so a skipped step is not mistaken for it. */
    private class Answer(val counted: Counted?)
}
