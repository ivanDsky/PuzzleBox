package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** A [moment] with the puzzle [day] it was earned on and the [game] of the solve that earned it. */
internal data class DatedMoment(val moment: Moment, val day: LocalDate, val game: GameId) {

    /** The Moment as a recap shows it (the Moments card, a month card's stamps); null for a friend Moment, never earned in a recap (A6). */
    fun earned(): EarnedMoment? = when (moment) {
        is Moment.PersonalBest -> EarnedMoment(EarnedMoment.Kind.PersonalBest, day, game)
        is Moment.FirstSubX -> EarnedMoment(EarnedMoment.Kind.FirstSubX, day, game, thresholdMs = moment.thresholdMs)
        is Moment.FullSetDone -> EarnedMoment(EarnedMoment.Kind.FullSet, day)
        is Moment.Streak -> EarnedMoment(EarnedMoment.Kind.Streak, day, streakDays = moment.days)
        is Moment.TopThreeFriends, is Moment.PassedFriend -> null
    }
}
