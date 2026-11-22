package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth

/**
 * The games, names and periods the recap screen tests and renders share. Each card's own fixture is an extension
 * property on this object in its own `Recap<Card>Fixtures.kt`, so a new card adds a file here instead of editing this one.
 */
internal object RecapFixtures {
    val zip = GameId("zip")
    val queens = GameId("queens")
    val sudoku = GameId("sudoku")

    private val names = mapOf(zip to "Zip", queens to "Queens", sudoku to "Sudoku", GameId("tango") to "Tango")

    val gameName: (GameId) -> String = { names.getValue(it) }

    val november = RecapPeriod.Month(YearMonth.of(2026, 11))
    val year2026 = RecapPeriod.Year(2026)
    val year2025 = RecapPeriod.Year(2025)
}
