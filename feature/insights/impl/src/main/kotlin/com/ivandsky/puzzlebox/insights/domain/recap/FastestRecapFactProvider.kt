package com.ivandsky.puzzlebox.insights.domain.recap

/** The Fastest solve card: one time per game played in the period. A period with no counted solve has no card (A2). */
internal object FastestRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val facts = input.facts
        val rows = input.inGameOrder(facts.fastestByGame).map { (game, solve) ->
            val solves = facts.solvedByGame[game] ?: 1
            RecapCard.Fastest.Row(
                game = game,
                timeMs = solve.timeMs,
                day = solve.day,
                puzzleNumber = solve.puzzleNumber,
                hasReplay = solve.hasReplay,
                averageMs = facts.averageTimeMsByGame[game]?.takeIf { solves > 1 && it > solve.timeMs },
                solves = solves,
            )
        }
        return if (rows.isEmpty()) emptyList() else listOf(RecapCard.Fastest(rows))
    }
}
