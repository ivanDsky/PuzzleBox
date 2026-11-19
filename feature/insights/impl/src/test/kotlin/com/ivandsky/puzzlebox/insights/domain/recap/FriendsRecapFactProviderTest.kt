package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendsRecapFactProviderTest {

    private suspend fun cardsOf(friends: FriendsStanding) =
        FriendsRecapFactProvider.cards(RecapInput(emptyFacts(), games = listOf(alpha), friends = friends))

    @Test
    fun `every shown answer is a card, in the state it came to`() = runTest {
        val loaded = FriendsStanding.Loaded(alpha, 410.0)
        val settling = FriendsStanding.Settling(LocalDate.of(2026, 12, 4))

        assertEquals(listOf(RecapCard.Friends(loaded)), cardsOf(loaded))
        assertEquals(listOf(RecapCard.Friends(settling)), cardsOf(settling))
        assertEquals(listOf(RecapCard.Friends(FriendsStanding.Unavailable)), cardsOf(FriendsStanding.Unavailable))
    }

    @Test
    fun `not applicable, and an answer not read yet, have no card`() = runTest {
        assertEquals(emptyList<RecapCard>(), cardsOf(FriendsStanding.NotApplicable))
        assertEquals(emptyList<RecapCard>(), cardsOf(FriendsStanding.Reading))
    }
}
