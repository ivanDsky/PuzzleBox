package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.CommunityRowModel

/**
 * One puzzle the page can list: its [row] as the list draws it, its [game], when it was created ([createdAtMs], null for a
 * friend's puzzle that is not in the community list).
 */
internal data class SeeAllEntry(
    val row: CommunityRowModel,
    val game: GameId,
    val createdAtMs: Long?,
)
