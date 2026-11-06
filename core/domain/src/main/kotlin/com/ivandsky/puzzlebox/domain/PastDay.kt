package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** One game's past [day]: its source [number] (null when unnumbered) and [PastDayStatus]. */
data class PastDay(val game: GameId, val day: LocalDate, val number: Int?, val status: PastDayStatus)
