package com.ivandsky.puzzlebox.insights.stats

import com.ivandsky.puzzlebox.insights.domain.recap.FastestSolve
import com.ivandsky.puzzlebox.insights.domain.recap.HintFreeRun
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.recap.StreakRun
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRecords
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** A stats page's states for the screen tests and the goldens: three games, one of them with an imported fastest solve. */
internal object StatsFixtures {
    val zip = GameId("zip")
    val queens = GameId("queens")
    val tango = GameId("tango")
    val games = listOf(zip, queens, tango)

    fun gameName(game: GameId): String = when (game) {
        zip -> "Zip"
        queens -> "Queens"
        tango -> "Tango"
        else -> game.value
    }

    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 11, dayOfMonth)

    val zipFastest = StatsRecords.Fastest(zip, FastestSolve(day(3), puzzleNumber = 412, timeMs = 41_000, hasReplay = true))
    val queensFastest = StatsRecords.Fastest(queens, FastestSolve(day(9), puzzleNumber = 280, timeMs = 95_000, hasReplay = false))
    val tangoFastest = StatsRecords.Fastest(tango, FastestSolve(day(12), puzzleNumber = 133, timeMs = 128_000, hasReplay = true))

    val streakSolve = SolveRef(zip, day(14), puzzleNumber = 423, hasReplay = true)
    val hintFreeLast = SolveRef(queens, day(15), puzzleNumber = 291, hasReplay = false)

    val records = StatsRecords(
        fastest = listOf(zipFastest, queensFastest, tangoFastest),
        longestStreak = StatsRecords.Streak(StreakRun(day(1), day(14), frozen = setOf(day(6))), streakSolve),
        longestHintFreeRun = HintFreeRun(solves = 9, start = day(7), last = hintFreeLast),
    )

    fun ready(
        range: StatsRange = StatsRange.Month,
        game: GameId? = null,
        state: SectionState<StatsRecords> = SectionState.Ready(records),
    ) = StatsUiState(range, game, StatsBody.Ready(listOf(StatsSection.Records(state))))
}
