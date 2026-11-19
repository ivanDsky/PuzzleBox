package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
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

        assertEquals(FriendsStanding.Loaded(beta, 520.0), resolve(answers)(november, listOf(alpha, beta)))
    }

    @Test
    fun `a tie goes to the game listed first`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 400.0)), beta to flowOf(standings("me" to 400.0)))

        assertEquals(FriendsStanding.Loaded(alpha, 400.0), resolve(answers)(november, listOf(alpha, beta)))
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

        assertEquals(FriendsStanding.Loaded(beta, 80.0), resolve(onOne)(november, listOf(alpha, beta)))
        assertEquals(FriendsStanding.NotApplicable, resolve(onNone)(november, listOf(alpha, beta)))
    }

    @Test
    fun `on day 2 of the next month the answer is Settling on the 4th, with nothing read`() = runTest {
        val answer = resolve(emptyMap(), today = "2026-12-02T12:00:00Z")(november, listOf(alpha))

        assertEquals(FriendsStanding.Settling(LocalDate.of(2026, 12, 4)), answer)
        assertEquals(emptyList<Pair<RankWindow, List<GameId>>>(), reads)
    }

    @Test
    fun `days 1 to 3 settle and the 4th shows the final result`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 250.0)))

        listOf("2026-12-01T12:00:00Z", "2026-12-03T12:00:00Z").forEach { day ->
            assertEquals(FriendsStanding.Settling(LocalDate.of(2026, 12, 4)), resolve(answers, today = day)(november, listOf(alpha)))
        }
        assertEquals(FriendsStanding.Loaded(alpha, 250.0), resolve(answers, today = "2026-12-04T12:00:00Z")(november, listOf(alpha)))
    }

    @Test
    fun `only the month just ended settles, an older month and the current one are read`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 90.0)))
        val october = RecapPeriod.Month(YearMonth.of(2026, 10))
        val december = RecapPeriod.Month(YearMonth.of(2026, 12))

        assertEquals(FriendsStanding.Loaded(alpha, 90.0), resolve(answers, today = "2026-12-02T12:00:00Z")(october, listOf(alpha)))
        assertEquals(FriendsStanding.Loaded(alpha, 90.0), resolve(answers, today = "2026-12-02T12:00:00Z")(december, listOf(alpha)))
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

        assertEquals(FriendsStanding.Loaded(alpha, 120.0), resolve(answers)(november, listOf(alpha)))
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

    @Test
    fun `a year is not applicable until the yearly recap fills it`() = runTest {
        assertEquals(FriendsStanding.NotApplicable, resolve(emptyMap())(RecapPeriod.Year(2026), listOf(alpha)))
        assertEquals(emptyList<Pair<RankWindow, List<GameId>>>(), reads)
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

        assertEquals(FriendsStanding.Loaded(alpha, 150.0), resolve(mapOf(alpha to partialThenDone))(november, listOf(alpha)))
    }

    @Test
    fun `a failing read is Unavailable and logged`() = runTest {
        val failing = flow<OverallState> { throw IOException("no connection") }

        assertEquals(FriendsStanding.Unavailable, resolve(mapOf(alpha to failing))(november, listOf(alpha)))
        assertEquals(listOf("friends standing failed"), warnings)
    }
}
