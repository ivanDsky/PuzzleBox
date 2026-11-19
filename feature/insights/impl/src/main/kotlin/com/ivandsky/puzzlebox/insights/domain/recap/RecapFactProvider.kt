package com.ivandsky.puzzlebox.insights.domain.recap

/** One fact of a recap: it reads the period's [RecapInput] and offers its cards, or none. */
internal interface RecapFactProvider {

    /**
     * The cards for [input]; empty means it has nothing to say, and the story simply has no such card. It may throw:
     * the recap chain logs that and adds no card.
     */
    suspend fun cards(input: RecapInput): List<RecapCard>
}
