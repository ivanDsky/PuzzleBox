package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The friends answer: the best game from standings, Settling on days 1–3, no card signed out or without friends on the boards, and every way a read can fail. */
class ResolveFriendsStandingTest {

    private val fixture = RecapHistoryFixture()
    private val me = Identity(uid = "me", displayName = "Me", photoUrl = null)
    private val november = RecapPeriod.Month(YearMonth.of(2026, 11))
    private val reads = mutableListOf<Pair<RankWindow, List<GameId>>>()
    private val warnings = mutableListOf<String>()

    /** A board of [rows]; with only the viewer on it, one friend with a few points joins, as the friends boards have. */
    private fun standings(vararg rows: Pair<String, Double>) = OverallState.Complete(
        OverallStandings(
            (if (rows.any { it.first != "me" }) rows.toList() else rows.toList() + ("maya" to 1.0))
                .mapIndexed { index, (uid, total) -> OverallStandings.Row(uid, uid, index + 1, total, games = 10, truncated = false) },
        ),
    )

    private fun onlyMe(total: Double) = OverallState.Complete(
        OverallStandings(listOf(OverallStandings.Row("me", "Me", place = 1, total = total, games = 10, truncated = false))),
    )

    /** [answers] by game; a game not named has nobody on its boards. */
    private fun resolve(
        answers: Map<GameId, Flow<OverallState>>,
        viewer: Identity? = me,
        today: String = "2026-12-15T12:00:00Z",
        timeout: Duration = 15.seconds,
    ) = fixture.resolveFriends(
        viewer = viewer,
        friendsOverall = { window, games ->
            reads += window to games
            answers[games.single()] ?: flowOf(OverallState.Empty)
        },
        today = fixture.puzzleDayAt(today),
        failures = FailureLog { _, message, _ -> warnings += message },
        timeout = timeout,
    )

    @Test
    fun `the best game is the one with the most friends points for the viewer`() = runTest {
        val answers = mapOf(
            alpha to flowOf(standings("me" to 300.0, "maya" to 700.0)),
            beta to flowOf(standings("maya" to 900.0, "me" to 520.0)),
        )

        assertEquals(FriendsStanding.Loaded(beta, 520.0, friendCount = 1, friendNames = listOf("maya")), resolve(answers)(november, listOf(alpha, beta)))
    }

    @Test
    fun `a tie goes to the game listed first`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 400.0)), beta to flowOf(standings("me" to 400.0)))

        assertEquals(FriendsStanding.Loaded(alpha, 400.0, friendCount = 1, friendNames = listOf("maya")), resolve(answers)(november, listOf(alpha, beta)))
    }

    @Test
    fun `each played game is read once over the month window`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 1.0)), beta to flowOf(standings("me" to 2.0)))

        resolve(answers)(november, listOf(alpha, beta))

        assertEquals(
            listOf(RankWindow.Month(LocalDate.of(2026, 11, 1)) to listOf(alpha), RankWindow.Month(LocalDate.of(2026, 11, 1)) to listOf(beta)),
            reads,
        )
    }

    @Test
    fun `a game the viewer is not on adds nothing, and being on none gives no card`() = runTest {
        val onOne = mapOf(alpha to flowOf(OverallState.Empty), beta to flowOf(standings("me" to 80.0)))
        val onNone = mapOf(alpha to flowOf(OverallState.Empty), beta to flowOf(standings("maya" to 80.0)))

        assertEquals(FriendsStanding.Loaded(beta, 80.0, friendCount = 1, friendNames = listOf("maya")), resolve(onOne)(november, listOf(alpha, beta)))
        assertEquals(FriendsStanding.NotApplicable, resolve(onNone)(november, listOf(alpha, beta)))
    }

    @Test
    fun `days 1 to 3 keep Settling while friends are on the boards, whatever their points, a LinkedIn-only friend included`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 250.0, "linkedin-friend" to 80.0)))

        listOf("2026-12-01T12:00:00Z", "2026-12-02T12:00:00Z", "2026-12-03T12:00:00Z").forEach { day ->
            assertEquals(FriendsStanding.Settling(LocalDate.of(2026, 12, 4), friendCount = 1, friendNames = listOf("linkedin-friend")), resolve(answers, today = day)(november, listOf(alpha)))
        }
        assertEquals(FriendsStanding.Loaded(alpha, 250.0, friendCount = 1, friendNames = listOf("linkedin-friend")), resolve(answers, today = "2026-12-04T12:00:00Z")(november, listOf(alpha)))
    }

    @Test
    fun `the winning game's friends are counted and named in board order without the viewer, the names capped at 30`() = runTest {
        val crowd = (1..35).map { "friend$it" to 1.0 }
        val answers = mapOf(
            alpha to flowOf(standings("friend1" to 900.0, "me" to 300.0, "friend2" to 1.0)),
            beta to flowOf(standings("me" to 520.0, *crowd.toTypedArray())),
        )

        val best = resolve(answers)(november, listOf(alpha, beta)) as FriendsStanding.Loaded

        assertEquals(beta, best.game)
        assertEquals(35, best.friendCount)
        assertEquals(crowd.take(30).map { it.first }, best.friendNames)
    }

    @Test
    fun `a settling month counts each friend once across the games it read`() = runTest {
        val answers = mapOf(
            alpha to flowOf(standings("me" to 1.0, "maya" to 1.0, "li" to 1.0)),
            beta to flowOf(standings("maya" to 1.0, "me" to 1.0, "sam" to 1.0)),
        )

        val settling = resolve(answers, today = "2026-12-02T12:00:00Z")(november, listOf(alpha, beta)) as FriendsStanding.Settling

        assertEquals(3, settling.friendCount)
        assertEquals(listOf("maya", "li", "sam"), settling.friendNames)
    }

    @Test
    fun `days 1 to 3 with nobody but the viewer on the boards drop the card`() = runTest {
        val answers = mapOf(alpha to flowOf(onlyMe(250.0)))

        assertEquals(FriendsStanding.NotApplicable, resolve(answers, today = "2026-12-02T12:00:00Z")(november, listOf(alpha)))
    }

    @Test
    fun `days 1 to 3 keep Settling when the read goes offline, fails or never answers`() = runTest {
        val settling = FriendsStanding.Settling(LocalDate.of(2026, 12, 4))
        val day = "2026-12-02T12:00:00Z"

        assertEquals(settling, resolve(mapOf(alpha to flowOf(OverallState.Offline(standings = null))), today = day)(november, listOf(alpha)))
        assertEquals(settling, resolve(mapOf(alpha to flow { throw IOException("boom") }), today = day)(november, listOf(alpha)))
        assertEquals(settling, resolve(mapOf(alpha to flow { awaitCancellation() }), today = day, timeout = 1.seconds)(november, listOf(alpha)))
    }

    @Test
    fun `only the month just ended settles, an older month and the current one are read`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 90.0)))
        val october = RecapPeriod.Month(YearMonth.of(2026, 10))
        val december = RecapPeriod.Month(YearMonth.of(2026, 12))

        assertEquals(FriendsStanding.Loaded(alpha, 90.0, friendCount = 1, friendNames = listOf("maya")), resolve(answers, today = "2026-12-02T12:00:00Z")(october, listOf(alpha)))
        assertEquals(FriendsStanding.Loaded(alpha, 90.0, friendCount = 1, friendNames = listOf("maya")), resolve(answers, today = "2026-12-02T12:00:00Z")(december, listOf(alpha)))
    }

    @Test
    fun `signed out gives no card and reads nothing`() = runTest {
        assertEquals(FriendsStanding.NotApplicable, resolve(emptyMap(), viewer = null)(november, listOf(alpha)))
        assertEquals(FriendsStanding.NotApplicable, resolve(emptyMap(), viewer = null, today = "2026-12-02T12:00:00Z")(november, listOf(alpha)))
        assertEquals(emptyList<Pair<RankWindow, List<GameId>>>(), reads)
    }

    @Test
    fun `a read with nobody but the viewer on its boards drops the card`() = runTest {
        val answers = mapOf(alpha to flowOf(onlyMe(120.0)), beta to flowOf(OverallState.Empty))

        assertEquals(FriendsStanding.NotApplicable, resolve(answers)(november, listOf(alpha, beta)))
    }

    @Test
    fun `friends that are only on the boards, as LinkedIn connections are, count the same as duel partners`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 120.0, "linkedin-friend" to 80.0)))

        assertEquals(FriendsStanding.Loaded(alpha, 120.0, friendCount = 1, friendNames = listOf("linkedin-friend")), resolve(answers)(november, listOf(alpha)))
    }

    @Test
    fun `start holds the card as Reading for a signed-in month with plays, and reads nothing`() = runTest {
        assertEquals(FriendsStanding.Reading, resolve(emptyMap()).start(november, listOf(alpha)))
        assertEquals(emptyList<Pair<RankWindow, List<GameId>>>(), reads)
    }

    @Test
    fun `start is not applicable signed out, for a year and with nothing played, and Settling on day 2`() = runTest {
        assertEquals(FriendsStanding.NotApplicable, resolve(emptyMap(), viewer = null).start(november, listOf(alpha)))
        assertEquals(FriendsStanding.NotApplicable, resolve(emptyMap()).start(RecapPeriod.Year(2026), listOf(alpha)))
        assertEquals(FriendsStanding.NotApplicable, resolve(emptyMap()).start(november, emptyList()))
        assertEquals(
            FriendsStanding.Settling(LocalDate.of(2026, 12, 4)),
            resolve(emptyMap(), today = "2026-12-02T12:00:00Z").start(november, listOf(alpha)),
        )
    }

    @Test
    fun `a period with no played game has no card`() = runTest {
        assertEquals(FriendsStanding.NotApplicable, resolve(emptyMap())(november, emptyList()))
    }

    private val year = RecapPeriod.Year(2026)

    /** [count] kept weeks of [game] from Jan 5 2026 on, each worth [pointsPerDay]. */
    private fun kept(game: GameId, count: Int, pointsPerDay: Double, from: LocalDate = LocalDate.of(2026, 1, 5)) =
        (0 until count).map { RankHistoryStore.Week("me", game, from.plusWeeks(it.toLong()), pointsPerDay, friendCount = 3) }

    private fun resolveYear(weeks: Map<GameId, List<RankHistoryStore.Week>>, viewer: Identity? = me) = fixture.resolveFriends(
        viewer = viewer,
        friendsOverall = { window, games ->
            reads += window to games
            flowOf(OverallState.Empty)
        },
        rankHistory = { game -> flowOf(weeks[game].orEmpty()) },
        today = fixture.puzzleDayAt("2026-12-22T12:00:00Z"),
    )

    @Test
    fun `a year with six kept weeks is the average a day over them, based on six weeks, without a network read`() = runTest {
        val resolve = resolveYear(mapOf(alpha to kept(alpha, 6, 30.0)))

        assertEquals(FriendsStanding.Loaded(alpha, points = 30.0, weeks = 6), resolve.start(year, listOf(alpha)))
        assertEquals(FriendsStanding.Loaded(alpha, points = 30.0, weeks = 6), resolve(year, listOf(alpha)))
        assertEquals(emptyList<Pair<RankWindow, List<GameId>>>(), reads)
    }

    @Test
    fun `the average is over the kept weeks, not recomputed from the boards`() = runTest {
        val weeks = kept(alpha, 2, 10.0) + kept(alpha, 2, 20.0, from = LocalDate.of(2026, 3, 2))

        assertEquals(FriendsStanding.Loaded(alpha, points = 15.0, weeks = 4), resolveYear(mapOf(alpha to weeks)).start(year, listOf(alpha)))
    }

    @Test
    fun `fewer than four kept weeks is no friends card`() = runTest {
        assertEquals(FriendsStanding.NotApplicable, resolveYear(mapOf(alpha to kept(alpha, 3, 50.0))).start(year, listOf(alpha)))
    }

    @Test
    fun `weeks outside the year are ignored`() = runTest {
        val weeks = kept(alpha, 3, 50.0) + kept(alpha, 5, 50.0, from = LocalDate.of(2025, 6, 2))

        assertEquals(FriendsStanding.NotApplicable, resolveYear(mapOf(alpha to weeks)).start(year, listOf(alpha)))
        assertEquals(
            FriendsStanding.Loaded(alpha, points = 50.0, weeks = 5),
            resolveYear(mapOf(alpha to weeks)).start(RecapPeriod.Year(2025), listOf(alpha)),
        )
    }

    @Test
    fun `a year from imports alone, with no kept weeks, has no friends card`() = runTest {
        assertEquals(FriendsStanding.NotApplicable, resolveYear(emptyMap()).start(RecapPeriod.Year(2025), listOf(alpha)))
    }

    @Test
    fun `the game with the highest average wins, the first listed on a tie`() = runTest {
        val weeks = mapOf(alpha to kept(alpha, 4, 20.0), beta to kept(beta, 5, 25.0))
        assertEquals(FriendsStanding.Loaded(beta, points = 25.0, weeks = 5), resolveYear(weeks).start(year, listOf(alpha, beta)))

        val tied = mapOf(alpha to kept(alpha, 4, 20.0), beta to kept(beta, 6, 20.0))
        assertEquals(FriendsStanding.Loaded(alpha, points = 20.0, weeks = 4), resolveYear(tied).start(year, listOf(alpha, beta)))
    }

    @Test
    fun `a year signed out has no friends card even with kept weeks`() = runTest {
        assertEquals(FriendsStanding.NotApplicable, resolveYear(mapOf(alpha to kept(alpha, 6, 30.0)), viewer = null).start(year, listOf(alpha)))
    }

    @Test
    fun `a kept history that cannot be read is no friends card`() = runTest {
        val resolve = fixture.resolveFriends(viewer = me, rankHistory = { flow { throw IOException("disk") } })

        assertEquals(FriendsStanding.NotApplicable, resolve.start(year, listOf(alpha)))
    }

    @Test
    fun `offline is Unavailable even when another game answered`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 300.0)), beta to flowOf(OverallState.Offline(standings = null)))

        assertEquals(FriendsStanding.Unavailable, resolve(answers)(november, listOf(alpha, beta)))
    }

    @Test
    fun `a read that keeps loading ends Unavailable at the timeout`() = runTest {
        val never = flow<OverallState> {
            emit(OverallState.Loading)
            awaitCancellation()
        }

        assertEquals(FriendsStanding.Unavailable, resolve(mapOf(alpha to never))(november, listOf(alpha)))
    }

    @Test
    fun `a partial read is waited out, never read as a number`() = runTest {
        val partialThenDone = flow {
            emit(OverallState.Partial(OverallStandings(emptyList()), loaded = 1, total = 2))
            emit(standings("me" to 150.0))
        }

        assertEquals(FriendsStanding.Loaded(alpha, 150.0, friendCount = 1, friendNames = listOf("maya")), resolve(mapOf(alpha to partialThenDone))(november, listOf(alpha)))
    }

    @Test
    fun `a failing read is Unavailable and logged`() = runTest {
        val failing = flow<OverallState> { throw IOException("no connection") }

        assertEquals(FriendsStanding.Unavailable, resolve(mapOf(alpha to failing))(november, listOf(alpha)))
        assertEquals(listOf("friends standing failed"), warnings)
    }
}
