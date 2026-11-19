package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentsRecapFactProviderTest {

    private fun input(vararg moments: Moment) = RecapInput(facts = emptyFacts().copy(moments = moments.toList()), games = emptyList())

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

        assertEquals(
            listOf(RecapCard.Moments(personalBests = 2, firstSubX = 1, fullSets = 2, streakMilestones = listOf(7, 30))),
            cards,
        )
        assertEquals(7, (cards.single() as RecapCard.Moments).total)
    }

    @Test
    fun `friend moments are left out, and a period with only those has no card`() = runTest {
        assertEquals(
            emptyList<RecapCard>(),
            MomentsRecapFactProvider.cards(input(Moment.TopThreeFriends(1), Moment.PassedFriend("u1", "Bea"))),
        )
        assertEquals(
            listOf(RecapCard.Moments(personalBests = 1, firstSubX = 0, fullSets = 0, streakMilestones = emptyList())),
            MomentsRecapFactProvider.cards(input(Moment.PassedFriend("u1", "Bea"), Moment.PersonalBest)),
        )
    }
}
