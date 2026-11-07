package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/**
 * Listing row of one puzzle this user published under [code]; the board itself stays on the
 * kept draft whose reserved code is [code]. [authorTimeMs] and [authorHints] come from the
 * author's test solve. [solvesCount] is the last fetched solve count, taken at [solvesCountAt];
 * both stay null until first fetched. [unpublished] marks a withdrawn code.
 */
data class PublishedPuzzle(
    val code: ShareCode,
    val gameId: GameId,
    val variant: VariantId,
    val title: String,
    val authorTimeMs: Long,
    val authorHints: Int,
    val createdAt: Instant,
    val unpublished: Boolean,
    val solvesCount: Int?,
    val solvesCountAt: Instant?,
)
