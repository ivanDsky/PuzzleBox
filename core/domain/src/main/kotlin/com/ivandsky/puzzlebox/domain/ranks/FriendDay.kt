package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * What Friends scope ranks one game day among (F20): the viewer's [friends] and the LinkedIn [connections] of that
 * [board]'s day as the phone holds them, null with LinkedIn off.
 */
data class FriendDay(val board: LeaderboardRef, val friends: Set<String>, val connections: LinkedInDayView?) {

    /** The viewer's [place] among the [count] friends and connections ranked on the day. */
    data class Standing(val place: Int, val count: Int)

    /**
     * Where [viewerUid] stands on the day's [entries] as the hub's Friends board ranks them ([rankFriendBoard], the
     * pipeline the hub's boards are built through). Null when the viewer has no entry on the board.
     */
    fun standing(entries: List<LeaderboardEntry>, viewerUid: String, order: RankOrder): Standing? {
        val ranked = rankFriendBoard(board, BoardPage(entries, entries.size), order, friends + viewerUid, connections, viewerUid)
        return ranked.rows.firstOrNull { it.entry.uid == viewerUid }?.let { Standing(it.rank, ranked.n) }
    }
}
