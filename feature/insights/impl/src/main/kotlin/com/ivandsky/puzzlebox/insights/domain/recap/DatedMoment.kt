package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** A [moment] with the puzzle [day] it was earned on and the [game] of the solve that earned it. */
internal data class DatedMoment(val moment: Moment, val day: LocalDate, val game: GameId)
