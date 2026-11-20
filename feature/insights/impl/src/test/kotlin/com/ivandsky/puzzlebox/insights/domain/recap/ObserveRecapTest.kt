package com.ivandsky.puzzlebox.insights.domain.recap

import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
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
    fun `the default chain, signed out, is the Solved, Fastest, Streak, Moments and Total time cards over the period's facts`() = runTest {
        fixture.store(
            fixture.solve(alpha, LocalDate.of(2026, 11, 1)),
            fixture.solve(alpha, LocalDate.of(2026, 11, 2)),
            fixture.solve(beta, LocalDate.of(2026, 11, 2)),
            fixture.playAgain(alpha, LocalDate.of(2026, 11, 2)),
        )

        val cards = defaultRecap(november).first()

        assertEquals(5, cards.size)
        assertEquals(
            RecapCard.Solved(3, listOf(RecapCard.Solved.PerGame(alpha, 2), RecapCard.Solved.PerGame(beta, 1)), hintFree = 3, longestHintFreeRun = 3),
            cards[0],
        )
        assertEquals(listOf(alpha, beta), (cards[1] as RecapCard.Fastest).rows.map { it.game })
        assertEquals(
            RecapCard.Streak(
                days = 2, from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 2), freezesSpent = 0,
                frozenDays = emptySet(), span = november,
            ),
            cards[2],
        )
        // Each game's first, quick solve went under a mark, and Nov 2 finished both games' day.
        assertEquals(RecapCard.Moments(personalBests = 0, firstSubX = 2, fullSets = 1, streakMilestones = emptyList()), cards[3])
        assertEquals(
            RecapCard.TotalTime(
                totalMs = 90_000,
                solved = 3,
                days = listOf(
                    DayTally(LocalDate.of(2026, 11, 1), solved = 1, timeMs = 30_000, hintFree = 1),
                    DayTally(LocalDate.of(2026, 11, 2), solved = 2, timeMs = 60_000, hintFree = 2),
                ),
                period = november,
            ),
            cards[4],
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
            fixture.computePeriodFacts,
            fixture.registry,
            fixture.resolveFriends(),
            listOf(SolvedRecapFactProvider, offline()),
            failures,
        )(november).first()

        assertEquals(listOf(RecapCard.Solved(1, listOf(RecapCard.Solved.PerGame(alpha, 1)), hintFree = 1, longestHintFreeRun = 1)), cards)
    }

    /** The friends read as the story sees it: [read] answers the one read of every game, [today] sets the puzzle day. */
    private fun friendsRecap(
        viewer: Identity? = Identity("me", "Me", photoUrl = null),
        today: String = "2026-12-15T12:00:00Z",
        read: (GameId) -> Flow<OverallState>,
    ) = ObserveRecap(
        fixture.observeHistory,
        fixture.computePeriodFacts,
        fixture.registry,
        fixture.resolveFriends(
            viewer = viewer,
            friendsOverall = { _, games -> read(games.single()) },
            today = fixture.puzzleDayAt(today),
        ),
    )

    private fun standings(total: Double, friend: String = "maya") = OverallState.Complete(
        OverallStandings(
            listOf(
                OverallStandings.Row("me", "Me", place = 1, total = total, games = 9, truncated = false),
                OverallStandings.Row(friend, friend, place = 2, total = total / 2, games = 9, truncated = false),
            ),
        ),
    )

    private fun storeNovember() = fixture.store(
        fixture.solve(alpha, LocalDate.of(2026, 11, 1)),
        fixture.solve(alpha, LocalDate.of(2026, 11, 2)),
        fixture.solve(beta, LocalDate.of(2026, 11, 2)),
    )

    private fun List<RecapCard>.kinds() = map { it::class.simpleName }

    @Test
    fun `the friends card is the fifth card, before total time, with every card present`() = runTest {
        storeNovember()
        val recap = friendsRecap { flowOf(standings(if (it == beta) 640.0 else 210.0)) }

        recap(november).test {
            val placeholder = awaitItem()
            assertEquals(
                listOf("Solved", "Fastest", "Streak", "Moments", "Friends", "TotalTime"),
                placeholder.kinds(),
            )
            assertEquals(RecapCard.Friends(FriendsStanding.Reading), placeholder[4])
            val settled = awaitItem()
            assertEquals(placeholder.kinds(), settled.kinds())
            assertEquals(RecapCard.Friends(FriendsStanding.Loaded(beta, 640.0)), settled[4])
            awaitComplete()
        }
    }

    @Test
    fun `the local story is out with the placeholder before the friends read answers, then settles in place`() = runTest {
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
            assertEquals(RecapCard.Friends(FriendsStanding.Reading), local[4])

            gate.complete(Unit)

            val settled = awaitItem()
            assertEquals(local.size, settled.size)
            assertEquals(local.filterNot { it is RecapCard.Friends }, settled.filterNot { it is RecapCard.Friends })
            assertEquals(RecapCard.Friends(FriendsStanding.Loaded(beta, 640.0)), settled[4])
            awaitComplete()
        }
    }

    @Test
    fun `friends who are only LinkedIn connections still give the card, the read is the one source`() = runTest {
        storeNovember()
        // No duel partner is known to the story; the standings alone carry a connection among the rows.
        val recap = friendsRecap { flowOf(standings(300.0, friend = "linkedin:connection")) }

        recap(november).test {
            awaitItem()
            val settled = awaitItem()
            assertEquals(RecapCard.Friends(FriendsStanding.Loaded(alpha, 300.0)), settled[4])
            awaitComplete()
        }
    }

    @Test
    fun `a read that finds no friends takes the card out`() = runTest {
        storeNovember()
        val onlyMe = OverallState.Complete(
            OverallStandings(listOf(OverallStandings.Row("me", "Me", place = 1, total = 50.0, games = 9, truncated = false))),
        )
        val recap = friendsRecap { flowOf(onlyMe) }

        recap(november).test {
            assertEquals(6, awaitItem().size)
            val settled = awaitItem()
            assertEquals(5, settled.size)
            assertEquals(false, settled.any { it is RecapCard.Friends })
            awaitComplete()
        }
    }

    @Test
    fun `offline, every other card is there and the friends card becomes Unavailable in place`() = runTest {
        storeNovember()
        val recap = friendsRecap { flowOf(OverallState.Offline(standings = null)) }

        recap(november).test {
            val local = awaitItem()
            assertEquals(6, local.size)
            val settled = awaitItem()
            assertEquals(local.map { if (it is RecapCard.Friends) RecapCard.Friends(FriendsStanding.Unavailable) else it }, settled)
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
            assertEquals(RecapCard.Friends(FriendsStanding.Unavailable), awaitItem()[4])
            awaitComplete()
        }
    }

    @Test
    fun `on day 2 of the next month the card says results settle on the 4th at once and stays while friends are there`() = runTest {
        storeNovember()
        val gate = CompletableDeferred<Unit>()
        val recap = friendsRecap(today = "2026-12-02T12:00:00Z") { flow { gate.await(); emit(standings(100.0)) } }
        val settling = RecapCard.Friends(FriendsStanding.Settling(LocalDate.of(2026, 12, 4)))

        recap(november).test {
            assertEquals(settling, awaitItem()[4])
            gate.complete(Unit)
            awaitComplete()
        }
    }

    @Test
    fun `on day 2 of the next month a read that finds no friends takes the settling card out`() = runTest {
        storeNovember()
        val onlyMe = OverallState.Complete(
            OverallStandings(listOf(OverallStandings.Row("me", "Me", place = 1, total = 50.0, games = 9, truncated = false))),
        )
        val recap = friendsRecap(today = "2026-12-02T12:00:00Z") { flowOf(onlyMe) }

        recap(november).test {
            val local = awaitItem()
            assertEquals(RecapCard.Friends(FriendsStanding.Settling(LocalDate.of(2026, 12, 4))), local[4])
            val settled = awaitItem()
            assertEquals(local.filterNot { it is RecapCard.Friends }, settled)
            awaitComplete()
        }
    }

    @Test
    fun `signed out, the story has no friends card and is told once`() = runTest {
        storeNovember()

        friendsRecap(viewer = null) { error("a signed-out viewer reads no board") }(november).test {
            val story = awaitItem()
            assertEquals(5, story.size)
            assertEquals(false, story.any { it is RecapCard.Friends })
            awaitComplete()
        }
    }

    @Test
    fun `a year's friends card comes from the kept weeks in the first emission and there is no second`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 3, 1)))
        val weeks = (0 until 6).map { RankHistoryStore.Week("me", alpha, LocalDate.of(2026, 1, 5).plusWeeks(it.toLong()), 40.0, friendCount = 2) }
        val recap = ObserveRecap(
            fixture.observeHistory,
            fixture.computePeriodFacts,
            fixture.registry,
            fixture.resolveFriends(viewer = Identity("me", "Me", photoUrl = null), rankHistory = { flowOf(weeks) }),
        )

        recap(RecapPeriod.Year(2026)).test {
            val friends = awaitItem().filterIsInstance<RecapCard.Friends>().single()
            assertEquals(FriendsStanding.Loaded(alpha, points = 40.0, weeks = 6), friends.state)
            awaitComplete()
        }
    }

    @Test
    fun `a year of imports has no friends card and says how many days it covers`() = runTest {
        fixture.store(fixture.imported(alpha, LocalDate.of(2025, 3, 1)), fixture.imported(beta, LocalDate.of(2025, 3, 1)), fixture.imported(alpha, LocalDate.of(2025, 4, 2)))
        val recap = ObserveRecap(
            fixture.observeHistory,
            fixture.computePeriodFacts,
            fixture.registry,
            fixture.resolveFriends(viewer = Identity("me", "Me", photoUrl = null)),
        )

        recap(RecapPeriod.Year(2025)).test {
            val cards = awaitItem()
            assertEquals(emptyList<RecapCard>(), cards.filterIsInstance<RecapCard.Friends>())
            assertEquals(2, cards.filterIsInstance<RecapCard.Solved>().single().importedDays)
            awaitComplete()
        }
    }

    @Test
    fun `a year's month cards follow the totals`() = runTest {
        fixture.store(fixture.solve(alpha, LocalDate.of(2026, 3, 1)), fixture.solve(alpha, LocalDate.of(2026, 5, 2)))

        val cards = defaultRecap(RecapPeriod.Year(2026)).first()

        val solvedAt = cards.indexOfFirst { it is RecapCard.Solved }
        val months = cards.filterIsInstance<RecapCard.Month>()
        assertEquals(listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 5)), months.map { it.month })
        assertEquals(solvedAt + 1, cards.indexOf(months.first()))
    }
}
