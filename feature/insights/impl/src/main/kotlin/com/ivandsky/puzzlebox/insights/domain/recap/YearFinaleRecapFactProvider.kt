package com.ivandsky.puzzlebox.insights.domain.recap

import java.time.YearMonth

/**
 * The finale of a yearly recap: the year's total, its twelve months and the three bests. Ties go to the earlier month
 * for the best month and the fastest solve, and to the game the app lists first for the most played. A month's own
 * recap has none, and a year with no counted solve has none.
 */
internal object YearFinaleRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val period = input.facts.period as? RecapPeriod.Year ?: return emptyList()
        val months = input.facts.months
        val best = months.maxByOrNull { it.solved } ?: return emptyList()
        val fastest = months.minBy { it.fastestTimeMs }
        val (mostPlayed, mostPlayedSolved) = input.inGameOrder(input.facts.solvedByGame).maxByOrNull { it.second } ?: return emptyList()
        val byMonth = months.associateBy { it.month }
        return listOf(
            RecapCard.YearFinale(
                total = input.facts.solved,
                months = (1..MonthsInYear).map { number ->
                    val month = YearMonth.of(period.year, number)
                    val facts = byMonth[month]
                    RecapCard.YearFinale.Tile(month, solved = facts?.solved ?: 0, game = facts?.bestGame)
                },
                bestMonth = best.month,
                bestMonthSolved = best.solved,
                fastestGame = fastest.fastestGame,
                fastestTimeMs = fastest.fastestTimeMs,
                fastestMonth = fastest.month,
                mostPlayedGame = mostPlayed,
                mostPlayedSolved = mostPlayedSolved,
                importedDays = input.facts.importedDays,
            ),
        )
    }

    private const val MonthsInYear = 12
}
