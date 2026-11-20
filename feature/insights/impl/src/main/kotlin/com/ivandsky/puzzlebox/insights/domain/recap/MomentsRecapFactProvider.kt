package com.ivandsky.puzzlebox.insights.domain.recap

/**
 * The Moments earned card: the period's Moments, oldest day first. Friend Moments are never earned here (A6), so they
 * are left out whatever the facts hold. A period that earned none has no card.
 */
internal object MomentsRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val earned = input.facts.moments.mapNotNull { it.earned() }
        return if (earned.isEmpty()) emptyList() else listOf(RecapCard.Moments(earned))
    }
}
