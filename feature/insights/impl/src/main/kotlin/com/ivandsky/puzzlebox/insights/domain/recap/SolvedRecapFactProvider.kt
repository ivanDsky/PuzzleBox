package com.ivandsky.puzzlebox.insights.domain.recap

/** The Puzzles solved card: the total and each game's share. A period with no counted solve has no card (A2). */
internal object SolvedRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val facts = input.facts
        if (facts.solved == 0) return emptyList()
        val perGame = input.inGameOrder(facts.solvedByGame).map { (game, solved) -> RecapCard.Solved.PerGame(game, solved) }
        return listOf(RecapCard.Solved(total = facts.solved, perGame = perGame))
    }
}
