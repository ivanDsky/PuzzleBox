package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * One board's page as the Friends scope ranks it, the one place a Friends board is built (F18, F20, F21): only the
 * entries of [players] (the viewer and their friends) are kept and the page is as long as what is left, then the
 * day's LinkedIn [connections] as the phone holds them are merged in ([withConnections]); null adds none. The hub's
 * boards ([amongFriends]) and the entry points' place ([rankFriendBoard]) both come through here, so they cannot
 * disagree on who is ranked. Nothing is read: a friend past the board's read page is not on it.
 */
fun BoardPage.friendBoard(players: Set<String>, connections: LinkedInDayView?, viewerUid: String?): BoardPage {
    // Friends first: a connection is nobody's friend, so narrowing after the merge would drop every one of them.
    val kept = entries.filter { it.uid in players }
    val among = BoardPage(kept, kept.size)
    return if (connections == null) among else among.withConnections(connections, viewerUid)
}

/**
 * [page] as a Friends board, ranked: [friendBoard], then [rankBoard] by [order]. Places run 1..n among the friends
 * and the connections who played, and N counts them.
 */
fun rankFriendBoard(
    board: LeaderboardRef,
    page: BoardPage,
    order: RankOrder,
    players: Set<String>,
    connections: LinkedInDayView?,
    viewerUid: String?,
): RankedBoard = page.friendBoard(players, connections, viewerUid).let { rankBoard(board, it.entries, order, it.count) }

/**
 * These boards as the Friends scope ranks them (F18, F21): every loaded board is its [friendBoard] among [players],
 * with the LinkedIn day the phone has saved for it in [days] counted on it; a board without a saved day counts
 * friends only. Ranking the result with [rankBoard], [overall] or [gameWindowStandings] then gives places 1..n among
 * them, with N those who played, so percentile points and Overall are the friends' own.
 */
fun RankWindowBoards.amongFriends(
    players: Set<String>,
    days: Map<LeaderboardRef, LinkedInDayView> = emptyMap(),
    viewerUid: String? = null,
): RankWindowBoards = when (this) {
    RankWindowBoards.SignedOut -> this
    is RankWindowBoards.Boards -> RankWindowBoards.Boards(
        boards.map { load ->
            if (load is BoardLoad.Loaded) load.copy(page = load.page.friendBoard(players, days[load.board], viewerUid)) else load
        },
    )
}
