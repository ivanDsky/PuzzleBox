package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import java.time.LocalDate

private val Streak = RecapCard.Streak(
    days = 17, from = LocalDate.of(2026, 11, 3), to = LocalDate.of(2026, 11, 19), freezesSpent = 2,
    frozenDays = setOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)), span = RecapFixtures.november,
)

/** A 17-day streak, two of its days held by freezes. */
internal val RecapFixtures.streak: RecapCard.Streak get() = Streak
