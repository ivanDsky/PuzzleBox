package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * One board's page as the Friends scope ranks it, the one place a Friends board is built (F18, F20, F21): only the
 * entries of [players] (the viewer and their friends) are kept and the page is as long as what is left, then the
 * day's LinkedIn [connections] as the phone holds them are merged in for [viewer] ([withConnections]); null adds none.
 * The [viewer]'s own entry [viewerEntry], when given, is one of the kept: the page is cut at the cap, so a viewer who
 * placed past it has no entry on it, yet they are on this day's board among their friends (`beyondPageEntries`). So are
 * the [friendEntries], the entries of friends who placed past the page ([LoadFriendsPastPage]), unless they are on it.
 * The hub's boards ([amongFriends]) and the entry points' place ([rankFriendBoard]) both come through here, so they
 * cannot disagree on who is ranked. Nothing is read here: a friend past the board's read page is on it only through
 * [friendEntries].
 */
fun BoardPage.friendBoard(
    players: Set<String>,
    connections: LinkedInDayView?,
    viewer: Identity?,
    viewerEntry: LeaderboardEntry? = null,
    friendEntries: List<LeaderboardEntry> = emptyList(),
): BoardPage {
    // Friends first: a connection is nobody's friend, so narrowing after the merge would drop every one of them.
    val onPage = entries.mapTo(HashSet()) { it.uid }
    val kept = entries.filter { it.uid in players } +
        friendEntries.filter { it.uid in players && onPage.add(it.uid) } +
        listOfNotNull(viewerEntry)
    val among = BoardPage(kept, kept.size)
    return if (connections == null) among else among.withConnections(connections, viewer)
}

/**
 * [page] as a Friends board, ranked: [friendBoard] (with the [viewerEntry] and [friendEntries] it names), then [rankBoard] by [order]. Places run 1..n among the friends
 * and the connections who played, and N counts them.
 */
fun rankFriendBoard(
    board: LeaderboardRef,
    page: BoardPage,
    order: RankOrder,
    players: Set<String>,
    connections: LinkedInDayView?,
    viewer: Identity?,
    viewerEntry: LeaderboardEntry? = null,
    friendEntries: List<LeaderboardEntry> = emptyList(),
): RankedBoard = page.friendBoard(players, connections, viewer, viewerEntry, friendEntries).let { rankBoard(board, it.entries, order, it.count) }

/**
 * These boards as the Friends scope ranks them (F18, F21): every loaded board is its [friendBoard] among [players],
 * with the LinkedIn day the phone has saved for it in [days] counted on it for [viewer]; a board without a saved day
 * counts friends only. [viewerEntries] has the viewer's own entry of a board they placed past the page of
 * (`beyondPageEntries`), [friendEntries] the entries friends who placed past a board's page have on it. Ranking the result
 * with [rankBoard], `overall` or `gameWindowStandings` then gives places 1..n among them, with N those who played, so
 * percentile points and Overall are the friends' own.
 */
fun RankWindowBoards.amongFriends(
    players: Set<String>,
    days: Map<LeaderboardRef, LinkedInDayView> = emptyMap(),
    viewer: Identity? = null,
    viewerEntries: Map<LeaderboardRef, LeaderboardEntry> = emptyMap(),
    friendEntries: Map<LeaderboardRef, List<LeaderboardEntry>> = emptyMap(),
): RankWindowBoards = when (this) {
    RankWindowBoards.SignedOut -> this
    is RankWindowBoards.Boards -> RankWindowBoards.Boards(
        boards.map { load ->
            if (load is BoardLoad.Loaded) {
                val page = load.page.friendBoard(players, days[load.board], viewer, viewerEntries[load.board], friendEntries[load.board].orEmpty())
                load.copy(page = page)
            } else {
                load
            }
        },
    )
}
