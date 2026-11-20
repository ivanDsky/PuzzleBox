package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment

/**
 * The Moments earned card: the period's Moments, oldest day first. Friend Moments are never earned here (A6), so they
 * are left out whatever the facts hold. A period that earned none has no card.
 */
internal object MomentsRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val earned = input.facts.moments.mapNotNull { it.earned() }
        return if (earned.isEmpty()) emptyList() else listOf(RecapCard.Moments(earned))
    }

    private fun DatedMoment.earned(): EarnedMoment? = when (moment) {
        is Moment.PersonalBest -> EarnedMoment(EarnedMoment.Kind.PersonalBest, day, game)
        is Moment.FirstSubX -> EarnedMoment(EarnedMoment.Kind.FirstSubX, day, game, thresholdMs = moment.thresholdMs)
        is Moment.FullSetDone -> EarnedMoment(EarnedMoment.Kind.FullSet, day)
        is Moment.Streak -> EarnedMoment(EarnedMoment.Kind.Streak, day, streakDays = moment.days)
        is Moment.TopThreeFriends, is Moment.PassedFriend -> null
    }
}
