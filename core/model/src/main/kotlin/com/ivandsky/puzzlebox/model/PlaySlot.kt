package com.ivandsky.puzzlebox.model

import java.time.LocalDate

/** The identity of one play: one daily ([day]) of one game ([gameId]); at most one saved play each (A10). */
data class PlaySlot(val gameId: GameId, val day: LocalDate)
