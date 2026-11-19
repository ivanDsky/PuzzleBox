package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The recap chain: its cards in list order, and one bad provider never costing the story. */
class ObserveRecapTest {

    private val fixture = RecapHistoryFixture()
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))
    private val warnings = mutableListOf<Pair<String, String>>()
    private val failures = FailureLog { tag, message, _ -> warnings += tag to message }

    private val defaultRecap = ObserveRecap(fixture.observeHistory, ComputePeriodFacts(), fixture.registry)

    private fun recap(vararg providers: RecapFactProvider) =
        ObserveRecap(fixture.observeHistory, ComputePeriodFacts(), fixture.registry, providers.toList(), failures)

    /** Offers [cards], or throws [failure] when given one: a provider that reached for the network and found none. */
    private class FakeRecapFactProvider(
        private val cards: List<RecapCard> = emptyList(),
        private val failure: Throwable? = null,
    ) : RecapFactProvider {
        override suspend fun cards(input: RecapInput): List<RecapCard> = failure?.let { throw it } ?: cards
    }

    private fun offline() = FakeRecapFactProvider(failure = IOException("no connection"))

    private val cardA = RecapCard.Solved(total = 99, perGame = emptyList())

    @Test
    fun `the default chain has a Solved card with the period's counts`() = runTest {
        fixture.store(
            fixture.solve(alpha, LocalDate.of(2026, 11, 1)),
            fixture.solve(alpha, LocalDate.of(2026, 11, 2)),
            fixture.solve(beta, LocalDate.of(2026, 11, 2)),
            fixture.playAgain(alpha, LocalDate.of(2026, 11, 2)),
        )

        assertEquals(
            listOf(RecapCard.Solved(3, listOf(RecapCard.Solved.PerGame(alpha, 2), RecapCard.Solved.PerGame(beta, 1)))),
            defaultRecap(november).first(),
        )
    }

    @Test
    fun `a period with no counted solve has no card`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 10, 31)))

        assertEquals(emptyList<RecapCard>(), defaultRecap(november).first())
    }

    @Test
    fun `cards follow the provider list's order, a provider with nothing adds none`() = runTest {
        val cardB = RecapCard.Solved(total = 1, perGame = emptyList())

        assertEquals(
            listOf(cardA, cardB),
            recap(FakeRecapFactProvider(listOf(cardA)), FakeRecapFactProvider(emptyList()), FakeRecapFactProvider(listOf(cardB)))(november).first(),
        )
    }

    @Test
    fun `a throwing provider adds no card, is logged once, and the rest still show`() = runTest {
        val cards = recap(offline(), FakeRecapFactProvider(listOf(cardA)))(november).first()

        assertEquals(listOf(cardA), cards)
        assertEquals(listOf("ObserveRecap" to "recap provider FakeRecapFactProvider failed"), warnings)
    }

    @Test
    fun `the story opens with its first card while a network-style provider throws`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 11, 1)))
        val cards = ObserveRecap(
            fixture.observeHistory,
            ComputePeriodFacts(),
            fixture.registry,
            listOf(SolvedRecapFactProvider, offline()),
            failures,
        )(november).first()

        assertEquals(listOf(RecapCard.Solved(1, listOf(RecapCard.Solved.PerGame(alpha, 1)))), cards)
    }
}
