package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId

/**
 * One game's ring on the home-screen widget: its id, name and Today's [status], the game's best solve time ([bestMs],
 * null with no completion), the player's [rank] and whether today's solve is a [newBest].
 */
data class WidgetRing(
    val game: GameId,
    val displayName: String,
    val status: TodayStatus,
    val bestMs: Long?,
    val rank: WidgetRank,
    val newBest: Boolean,
)
