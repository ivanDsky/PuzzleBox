package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage

/**
 * These boards as the Friends scope ranks them (F18): every loaded board keeps only the entries of [players]
 * (the viewer and their friends) and is as long as what is left. Ranking the result with [rankBoard], [overall]
 * or [gameWindowStandings] then gives places 1..n among friends, with N the friends who played, so percentile
 * points and Overall are the friends' own. Nothing is read: a friend past a board's read page is not on it.
 */
fun RankWindowBoards.amongFriends(players: Set<String>): RankWindowBoards = when (this) {
    RankWindowBoards.SignedOut -> this
    is RankWindowBoards.Boards -> RankWindowBoards.Boards(
        boards.map { load ->
            if (load is BoardLoad.Loaded) {
                val kept = load.page.entries.filter { it.uid in players }
                load.copy(page = BoardPage(kept, kept.size))
            } else {
                load
            }
        },
    )
}
