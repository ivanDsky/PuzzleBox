package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * One daily board's load state inside a window (A29). A third load tree on purpose:
 * `TodayLeaderboard.Board` holds a `LeaderboardSnapshot`, which has no N, and a window needs
 * every board's N for percentile points, so [Loaded] holds a [BoardPage].
 */
sealed interface BoardLoad {
    val board: LeaderboardRef

    /** The board's game's rank order; a loaded page's entries are ranked by it. */
    val order: RankOrder

    /** No read has answered yet. */
    data class Loading(override val board: LeaderboardRef, override val order: RankOrder) : BoardLoad

    /** [final]: the board can never change again (A22), so [page] is the whole answer for good. */
    data class Loaded(
        override val board: LeaderboardRef,
        override val order: RankOrder,
        val page: BoardPage,
        val final: Boolean,
    ) : BoardLoad

    /** A12: the board or its N can't be read right now, so it adds nothing and is not counted as played. */
    data class Unavailable(override val board: LeaderboardRef, override val order: RankOrder) : BoardLoad
}
