package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.DayTally
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import java.time.LocalDate

private val TotalTime = RecapCard.TotalTime(
    totalMs = 11_535_000,
    solved = 42,
    days = listOf(DayTally(LocalDate.of(2026, 11, 3), solved = 42, timeMs = 11_535_000, hintFree = 36)),
    period = RecapFixtures.november,
)

/** Three hours and a bit over 42 solves. */
internal val RecapFixtures.totalTime: RecapCard.TotalTime get() = TotalTime
