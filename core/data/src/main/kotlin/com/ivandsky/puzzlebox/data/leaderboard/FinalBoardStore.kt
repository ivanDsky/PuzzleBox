package com.ivandsky.puzzlebox.data.leaderboard

/**
 * Where Final boards live between reads: a stored board is a [BoardPage] as the cloud returned it,
 * so the page's order is the caller's (ranking stays in the domain). A board never stored reads null;
 * a stored empty board reads `BoardPage(emptyList(), 0)`.
 */
interface FinalBoardStore {
    suspend fun get(board: LeaderboardRef): BoardPage?

    /** Replaces the whole stored board of [board]. */
    suspend fun put(board: LeaderboardRef, page: BoardPage)
}
