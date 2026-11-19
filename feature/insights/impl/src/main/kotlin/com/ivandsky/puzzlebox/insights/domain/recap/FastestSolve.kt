package com.ivandsky.puzzlebox.insights.domain.recap

import java.time.LocalDate

/**
 * A game's fastest counted solve of a period: the puzzle [day] and [puzzleNumber] it was of, and its time. [hasReplay]
 * is false for an imported result, which carries a time and nothing else to watch (A5).
 */
internal data class FastestSolve(
    val day: LocalDate,
    val puzzleNumber: Int?,
    val timeMs: Long,
    val hasReplay: Boolean,
)
