package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import java.time.LocalDate

private val Fastest = RecapCard.Fastest(
    rows = listOf(
        RecapCard.Fastest.Row(RecapFixtures.zip, timeMs = 58_000, day = LocalDate.of(2026, 11, 3), puzzleNumber = 212, hasReplay = true, averageMs = 91_000, solves = 6),
        RecapCard.Fastest.Row(RecapFixtures.queens, timeMs = 141_000, day = LocalDate.of(2026, 11, 12), puzzleNumber = 190, hasReplay = true, averageMs = 215_000, solves = 4),
        RecapCard.Fastest.Row(RecapFixtures.sudoku, timeMs = 312_000, day = LocalDate.of(2026, 11, 8), puzzleNumber = 77, hasReplay = false, averageMs = null, solves = 1),
    ),
)

/** Fastest solves: two games with a replay, one imported time without. */
internal val RecapFixtures.fastest: RecapCard.Fastest get() = Fastest
