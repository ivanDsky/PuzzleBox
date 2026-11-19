package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment

/**
 * The Moments earned card: the period's Moments counted by kind. Friend Moments are never earned here (A6), so they
 * are left out whatever the facts hold. A period that earned none has no card.
 */
internal object MomentsRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val moments = input.facts.moments
        val card = RecapCard.Moments(
            personalBests = moments.count { it is Moment.PersonalBest },
            firstSubX = moments.count { it is Moment.FirstSubX },
            fullSets = moments.count { it is Moment.FullSetDone },
            streakMilestones = moments.filterIsInstance<Moment.Streak>().map { it.days },
        )
        return if (card.total == 0) emptyList() else listOf(card)
    }
}
