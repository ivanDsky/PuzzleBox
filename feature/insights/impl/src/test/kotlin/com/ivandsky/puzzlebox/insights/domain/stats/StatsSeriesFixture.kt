package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.insights.domain.recap.PeriodFacts
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** The facts of a stored history over a stats range, on the puzzle day 2026-11-20 (a Friday), for the series tests. */
internal class StatsSeriesFixture {
    val history = RecapHistoryFixture()
    val today: LocalDate = LocalDate.of(2026, 11, 20)

    fun day(dayOfMonth: Int, month: Int = 11): LocalDate = LocalDate.of(2026, month, dayOfMonth)

    suspend fun facts(range: StatsRange, scope: GameId? = null): PeriodFacts {
        val all = history.history()
        val firstSolve = all.filter { scope == null || it.gameId == scope }.mapNotNull { if (it.record == null) null else it.day }.minOrNull()
        return history.computePeriodFacts(all, range.span(today, firstSolve), scope)
    }

    fun input(facts: PeriodFacts, range: StatsRange) = StatsInput(range, facts, games = emptyList())
}
