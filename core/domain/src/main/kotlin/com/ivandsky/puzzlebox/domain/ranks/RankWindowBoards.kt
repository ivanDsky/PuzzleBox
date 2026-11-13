package com.ivandsky.puzzlebox.domain.ranks

/** Every daily board of a window as the player may read it (A29). */
sealed interface RankWindowBoards {
    /** Boards are read only when signed in (A3), so nothing is listened to or fetched. */
    data object SignedOut : RankWindowBoards

    /** One [BoardLoad] per game and window day: the games in the order asked, each game's days ascending. */
    data class Boards(val boards: List<BoardLoad>) : RankWindowBoards
}
