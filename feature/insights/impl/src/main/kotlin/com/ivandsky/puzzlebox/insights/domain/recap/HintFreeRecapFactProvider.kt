package com.ivandsky.puzzlebox.insights.domain.recap

/** The Hint-free solves card: how many counted solves needed no hint. A period with none has no card. */
internal object HintFreeRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val facts = input.facts
        if (facts.hintFree == 0) return emptyList()
        return listOf(RecapCard.HintFree(hintFree = facts.hintFree, solved = facts.solved))
    }
}
