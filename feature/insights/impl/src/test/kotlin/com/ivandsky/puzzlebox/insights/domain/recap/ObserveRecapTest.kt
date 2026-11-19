package com.ivandsky.puzzlebox.insights.domain.recap

import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The recap chain: its cards in list order, and one bad provider never costing the story. */
class ObserveRecapTest {

    private val fixture = RecapHistoryFixture()
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))
    private val warnings = mutableListOf<Pair<String, String>>()
    private val failures = FailureLog { tag, message, _ -> warnings += tag to message }

    private val defaultRecap = ObserveRecap(fixture.observeHistory, fixture.computePeriodFacts, fixture.registry, fixture.resolveFriends())

    private fun recap(vararg providers: RecapFactProvider) =
        ObserveRecap(fixture.observeHistory, fixture.computePeriodFacts, fixture.registry, fixture.resolveFriends(), providers.toList(), failures)

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
    fun `the default chain is the Solved, Fastest, Streak, Hint-free, Moments and Total time cards over the period's facts`() = runTest {
        fixture.store(
            fixture.solve(alpha, LocalDate.of(2026, 11, 1)),
            fixture.solve(alpha, LocalDate.of(2026, 11, 2)),
            fixture.solve(beta, LocalDate.of(2026, 11, 2)),
            fixture.playAgain(alpha, LocalDate.of(2026, 11, 2)),
        )

        val cards = defaultRecap(november).first()

        assertEquals(6, cards.size)
        assertEquals(
            RecapCard.Solved(3, listOf(RecapCard.Solved.PerGame(alpha, 2), RecapCard.Solved.PerGame(beta, 1))),
            cards[0],
        )
        assertEquals(listOf(alpha, beta), (cards[1] as RecapCard.Fastest).rows.map { it.game })
        assertEquals(
            RecapCard.Streak(days = 2, from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 2), freezesSpent = 0),
            cards[2],
        )
        assertEquals(RecapCard.HintFree(hintFree = 3, solved = 3), cards[3])
        // Each game's first, quick solve went under a mark, and Nov 2 finished both games' day.
        assertEquals(RecapCard.Moments(personalBests = 0, firstSubX = 2, fullSets = 1, streakMilestones = emptyList()), cards[4])
        assertEquals(RecapCard.TotalTime(totalMs = 90_000, solved = 3), cards[5])
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
            fixture.computePeriodFacts,
            fixture.registry,
            fixture.resolveFriends(),
            listOf(SolvedRecapFactProvider, offline()),
            failures,
        )(november).first()

        assertEquals(listOf(RecapCard.Solved(1, listOf(RecapCard.Solved.PerGame(alpha, 1)))), cards)
    }

    /** The friends read as the story sees it: [read] answers the one read of every game, [today] sets the puzzle day. */
    private fun friendsRecap(today: String = "2026-12-15T12:00:00Z", read: (GameId) -> Flow<OverallState>) = ObserveRecap(
        fixture.observeHistory,
        fixture.computePeriodFacts,
        fixture.registry,
        fixture.resolveFriends(
            viewer = Identity("me", "Me", photoUrl = null),
            friendUids = { flowOf(setOf("maya")) },
            friendsOverall = { _, games -> read(games.single()) },
            today = fixture.puzzleDayAt(today),
        ),
    )

    private fun standings(total: Double) = OverallState.Complete(
        OverallStandings(listOf(OverallStandings.Row("me", "Me", place = 1, total = total, games = 9, truncated = false))),
    )

    private fun storeNovember() = fixture.store(
        fixture.solve(alpha, LocalDate.of(2026, 11, 1)),
        fixture.solve(alpha, LocalDate.of(2026, 11, 2)),
        fixture.solve(beta, LocalDate.of(2026, 11, 2)),
    )

    @Test
    fun `the local story is out before the friends read answers, then gains the friends card at the end`() = runTest {
        storeNovember()
        val gate = CompletableDeferred<Unit>()
        val recap = friendsRecap { game ->
            flow {
                gate.await()
                emit(standings(if (game == beta) 640.0 else 210.0))
            }
        }

        recap(november).test {
            val local = awaitItem()
            assertEquals(6, local.size)
            assertEquals(false, local.any { it is RecapCard.Friends })

            gate.complete(Unit)

            val full = awaitItem()
            assertEquals(local + RecapCard.Friends(FriendsStanding.Loaded(beta, 640.0)), full)
            awaitComplete()
        }
    }

    @Test
    fun `offline, every other card is there and the friends card is Unavailable`() = runTest {
        storeNovember()
        val recap = friendsRecap { flowOf(OverallState.Offline(standings = null)) }

        recap(november).test {
            val local = awaitItem()
            assertEquals(6, local.size)
            assertEquals(local + RecapCard.Friends(FriendsStanding.Unavailable), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a friends read that never answers leaves the local story whole, and the card says it cannot load`() = runTest {
        storeNovember()
        val recap = friendsRecap { flow { awaitCancellation() } }

        recap(november).test {
            val local = awaitItem()
            assertEquals(6, local.size)
            assertEquals(local + RecapCard.Friends(FriendsStanding.Unavailable), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `on day 2 of the next month the friends card says results settle on the 4th`() = runTest {
        storeNovember()
        val recap = friendsRecap(today = "2026-12-02T12:00:00Z") { error("a settling month reads no board") }

        recap(november).test {
            val local = awaitItem()
            assertEquals(local + RecapCard.Friends(FriendsStanding.Settling(LocalDate.of(2026, 12, 4))), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `signed out, the story has no friends card and is told once`() = runTest {
        storeNovember()

        defaultRecap(november).test {
            assertEquals(6, awaitItem().size)
            awaitComplete()
        }
    }
}
