package com.ivandsky.puzzlebox.insights.domain.recap

/** The Fastest solve card: one time per game played in the period. A period with no counted solve has no card (A2). */
internal object FastestRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val rows = input.inGameOrder(input.facts.fastestByGame).map { (game, solve) ->
            RecapCard.Fastest.Row(
                game = game,
                timeMs = solve.timeMs,
                day = solve.day,
                puzzleNumber = solve.puzzleNumber,
                hasReplay = solve.hasReplay,
            )
        }
        return if (rows.isEmpty()) emptyList() else listOf(RecapCard.Fastest(rows))
    }
}
