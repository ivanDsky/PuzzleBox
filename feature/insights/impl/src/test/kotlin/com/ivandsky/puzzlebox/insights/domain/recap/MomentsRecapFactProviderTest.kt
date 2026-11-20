package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MomentsRecapFactProviderTest {

    private fun input(vararg moments: Moment) = RecapInput(
        facts = emptyFacts().copy(moments = moments.mapIndexed { i, moment -> DatedMoment(moment, LocalDate.of(2026, 11, 1 + i), alpha) }),
        games = emptyList(),
    )

    private fun earned(kind: EarnedMoment.Kind, dayOfMonth: Int, thresholdMs: Long? = null, streakDays: Int? = null) =
        EarnedMoment(kind, LocalDate.of(2026, 11, dayOfMonth), alpha.takeIf { kind == EarnedMoment.Kind.PersonalBest || kind == EarnedMoment.Kind.FirstSubX }, thresholdMs, streakDays)

    @Test
    fun `a period that earned none has no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), MomentsRecapFactProvider.cards(input()))
    }

    @Test
    fun `the card counts each local kind and lists the streak milestones in order`() = runTest {
        val cards = MomentsRecapFactProvider.cards(
            input(
                Moment.PersonalBest,
                Moment.Streak(7),
                Moment.FullSetDone(),
                Moment.FirstSubX(60_000),
                Moment.PersonalBest,
                Moment.FullSetDone(Moment.FullSetDone.DayTotal(300_000, 0)),
                Moment.Streak(30),
            ),
        )

        val card = cards.single() as RecapCard.Moments
        assertEquals(2, card.personalBests)
        assertEquals(1, card.firstSubX)
        assertEquals(2, card.fullSets)
        assertEquals(listOf(7, 30), card.streakMilestones)
        assertEquals(7, card.total)
        // Each sticker keeps its own day, game and time, in the order earned.
        assertEquals(
            listOf(
                earned(EarnedMoment.Kind.PersonalBest, 1),
                earned(EarnedMoment.Kind.Streak, 2, streakDays = 7),
                earned(EarnedMoment.Kind.FullSet, 3),
                earned(EarnedMoment.Kind.FirstSubX, 4, thresholdMs = 60_000),
            ),
            card.earned.take(4),
        )
    }

    @Test
    fun `friend moments are left out, and a period with only those has no card`() = runTest {
        assertEquals(
            emptyList<RecapCard>(),
            MomentsRecapFactProvider.cards(input(Moment.TopThreeFriends(1), Moment.PassedFriend("u1", "Bea"))),
        )
        assertEquals(
            listOf(RecapCard.Moments(listOf(earned(EarnedMoment.Kind.PersonalBest, 2)))),
            MomentsRecapFactProvider.cards(input(Moment.PassedFriend("u1", "Bea"), Moment.PersonalBest)),
        )
    }
}
