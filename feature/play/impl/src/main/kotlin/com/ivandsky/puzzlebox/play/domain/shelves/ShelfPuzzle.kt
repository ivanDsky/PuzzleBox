package com.ivandsky.puzzlebox.play.domain.shelves

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * One loaded public puzzle as the community home sees it: the [code] that identifies it, its [game], when it was
 * created ([createdAtMs]) and how often it was solved ([solves], null while the solve counter does not exist).
 * [isFriend] and [isOwn] mark a friend's or the player's own puzzle; they flow through the shelves like any other.
 */
data class ShelfPuzzle(
    val code: ShareCode,
    val game: GameId,
    val createdAtMs: Long,
    val solves: Int?,
    val isFriend: Boolean = false,
    val isOwn: Boolean = false,
)
