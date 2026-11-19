package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.SolveFacts
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** One counted solve of the history, with the [facts] the Moment detector reads for it, as they stood when it was solved. */
internal data class ReplayedSolve(val game: GameId, val day: LocalDate, val facts: SolveFacts)
