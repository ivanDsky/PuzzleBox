package com.ivandsky.puzzlebox.insights.domain.recap

/** The Total time card: the play time of the period's counted solves (A8). A period with no counted solve has no card. */
internal object TotalTimeRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val facts = input.facts
        if (facts.solved == 0) return emptyList()
        return listOf(RecapCard.TotalTime(totalMs = facts.totalTimeMs, solved = facts.solved))
    }
}
