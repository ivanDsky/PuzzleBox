package com.ivandsky.puzzlebox.insights.domain.recap

/**
 * The month cards of a yearly recap: one for each month with a counted solve, January first, each with its count and
 * its best against the player's average (A3). They sit right after the totals cards (Total time is the last of them), before the year finale. A month's own recap is the month already,
 * so it gets none.
 */
internal object MonthsRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> =
        if (input.facts.period !is RecapPeriod.Year) {
            emptyList()
        } else {
            input.facts.months.map { RecapCard.Month(it.month, it.solved, it.bestGame, it.bestTimeMs, it.underAveragePercent) }
        }
}
