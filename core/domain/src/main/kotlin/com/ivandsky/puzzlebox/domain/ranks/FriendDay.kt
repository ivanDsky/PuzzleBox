package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * What Friends scope ranks one game day among (F20): the viewer's [friends] and the LinkedIn [connections] of that
 * [board]'s day as the phone holds them, null with LinkedIn off. [own] is the viewer's own records of the day: the
 * board is read to its page, so one placed past it is on the day's board only through them (`beyondPageEntries`, A11).
 * With [friendsPast] (backlog #1779) a friend who placed past it is read by id and counted, as on the hub; [memo] is
 * what the reads of one collection have asked, so a board is asked about a friend once while it is shown.
 */
data class FriendDay(
    val board: LeaderboardRef,
    val friends: Set<String>,
    val connections: LinkedInDayView?,
    val own: List<CompletionRecord> = emptyList(),
    val friendsPast: LoadFriendsPastPage? = null,
    val memo: LoadFriendsPastPage.Memo = LoadFriendsPastPage.Memo(),
) {

    /** The viewer's [place] among the [count] friends and connections ranked on the day. */
    data class Standing(val place: Int, val count: Int)

    /**
     * Where [viewer] stands on the day's [entries] as the hub's Friends board ranks them ([rankFriendBoard], the
     * pipeline the hub's boards are built through). Null when the viewer is not ranked on the day: no entry on the
     * board (not even past its page, [own]) and no LinkedIn result of their own for it. Suspends only for the
     * [friendsPast] read, which a full page of [entries] starts.
     */
    suspend fun standing(entries: List<LeaderboardEntry>, viewer: Identity, order: RankOrder): Standing? {
        val page = BoardPage(entries, entries.size)
        val loaded = BoardLoad.Loaded(board, order, page, final = false)
        val players = friends + viewer.uid
        val past = beyondPageEntries(listOf(loaded), viewer, own)[board]
        val friendEntries = friendsPast?.invoke(memo, RankWindowBoards.Boards(listOf(loaded)), players, viewer.uid)?.get(board).orEmpty()
        val ranked = rankFriendBoard(board, page, order, players, connections, viewer, past, friendEntries)
        return ranked.rows.firstOrNull { it.entry.uid == viewer.uid }?.let { Standing(it.rank, ranked.n) }
    }
}
