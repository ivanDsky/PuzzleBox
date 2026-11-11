package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost

/** The connections board of one game day, as the day card and the board draw it. */
sealed interface LinkedInBoardView {
    /** Not connected, or the board has not been read yet. */
    data object Hidden : LinkedInBoardView

    /** The day is outside the days LinkedIn keeps a connections board for. */
    data object OutsideWindow : LinkedInBoardView

    /**
     * [ghosts] are sorted by the game's rank order, hidden times last. [yourRank] is null unless
     * the player's own result is imported. With a rank, [total] is the "of N": the player and the connections with a time;
     * without one it is the connections that played, a hidden time included.
     */
    data class Shown(
        val ghosts: List<LinkedInGhost>,
        val yourRank: Int?,
        val total: Int,
        val updatedAtMs: Long,
    ) : LinkedInBoardView
}
