package com.ivandsky.puzzlebox.model

import java.time.LocalDate

/** The game and day a [DailyLink] names. */
data class DailyRef(val gameId: GameId, val day: LocalDate)
