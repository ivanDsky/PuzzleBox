package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId

/** One game's row in today's set: its identity, display name and [TodayStatus]. */
data class TodayEntry(
    val game: GameId,
    val displayName: String,
    val status: TodayStatus,
)
