package com.ivandsky.puzzlebox.insights.domain.recap

/** The Puzzles solved card: the total and each game's share. A period with no counted solve has no card (A2). */
internal object SolvedRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val facts = input.facts
        if (facts.solved == 0) return emptyList()
        // The app's games first, in its order; a game it no longer lists follows, so the rows still add up to the total.
        val unlisted = (facts.solvedByGame.keys - input.games.toSet()).sortedBy { it.value }
        val perGame = (input.games + unlisted).mapNotNull { game ->
            facts.solvedByGame[game]?.let { RecapCard.Solved.PerGame(game, it) }
        }
        return listOf(RecapCard.Solved(total = facts.solved, perGame = perGame))
    }
}
