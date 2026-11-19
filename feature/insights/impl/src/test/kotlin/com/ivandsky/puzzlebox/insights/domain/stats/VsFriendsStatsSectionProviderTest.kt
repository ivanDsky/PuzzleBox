package com.ivandsky.puzzlebox.insights.domain.stats

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding.Locked
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding.Ranked
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding.Unlock
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.io.IOException
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vs friends section: best and weakest game by Friends Overall points over the range's window, one game, signed out, no friends and every way a read can fail. */
class VsFriendsStatsSectionProviderTest {

    private val f = StatsSeriesFixture()
    private val reads = mutableListOf<Pair<RankWindow, List<GameId>>>()
    private val warnings = mutableListOf<String>()
    private val games = listOf(alpha, beta)

    private fun standings(vararg rows: Pair<String, Double>) = OverallState.Complete(
        OverallStandings(rows.mapIndexed { index, (uid, total) -> OverallStandings.Row(uid, uid, index + 1, total, games = 10, truncated = false) }),
    )

    /** The provider over [answers] by game; a game not named has nobody on its boards. */
    private fun provider(answers: Map<GameId, Flow<OverallState>>, viewer: Identity? = Identity("me", "Me", null), timeout: kotlin.time.Duration = 15.seconds) =
        f.vsFriends(viewer, timeout, warnings) { window, read ->
            reads += window to read
            answers[read.single()] ?: flowOf(OverallState.Empty)
        }

    private suspend fun solved(range: StatsRange = StatsRange.Month, scope: GameId? = null): StatsInput {
        f.history.store(f.history.solve(alpha, f.day(18)), f.history.solve(beta, f.day(18)))
        return f.input(f.facts(range, scope), range, games)
    }

    /** The section once its read has settled: after the loading emission. */
    private suspend fun StatsSectionProvider.settled(input: StatsInput): StatsSection? {
        var last: StatsSection? = null
        section(input).test {
            val first = awaitItem()
            last = first
            if (first is StatsSection.VsFriends && first.state == SectionState.Loading) last = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return last
    }

    private fun StatsSection?.ranked(): Ranked = ((this as StatsSection.VsFriends).state as SectionState.Ready).value as Ranked

    @Test
    fun `the best and weakest game are the viewer's most and fewest friends points`() = runTest {
        val answers = mapOf(
            alpha to flowOf(standings("me" to 300.0, "maya" to 700.0)),
            beta to flowOf(standings("maya" to 900.0, "me" to 520.0, "joe" to 100.0)),
        )

        val ranked = provider(answers).settled(solved()).ranked()

        assertEquals(GameStanding(beta, 520.0, place = 2, players = 3), ranked.best)
        assertEquals(GameStanding(alpha, 300.0, place = 1, players = 2), ranked.weakest)
    }

    @Test
    fun `it loads first, then answers`() = runTest {
        val input = solved()

        provider(mapOf(alpha to flowOf(standings("me" to 1.0, "maya" to 2.0)))).section(input).test {
            assertEquals(StatsSection.VsFriends(SectionState.Loading), awaitItem())
            assertTrue(awaitItem() is StatsSection.VsFriends)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a tie for best goes to the game listed first, and equal games show only the best`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 400.0, "maya" to 1.0)), beta to flowOf(standings("me" to 400.0, "maya" to 1.0)))

        val ranked = provider(answers).settled(solved()).ranked()

        assertEquals(alpha, ranked.best.game)
        assertNull(ranked.weakest)
    }

    @Test
    fun `a game the viewer is not on adds nothing, and one game shows only the best`() = runTest {
        val answers = mapOf(alpha to flowOf(OverallState.Empty), beta to flowOf(standings("me" to 80.0, "maya" to 90.0)))

        val ranked = provider(answers).settled(solved()).ranked()

        assertEquals(beta, ranked.best.game)
        assertNull(ranked.weakest)
    }

    @Test
    fun `a single game's page reads only that game`() = runTest {
        val answers = mapOf(beta to flowOf(standings("me" to 80.0, "maya" to 90.0)))

        val ranked = provider(answers).settled(solved(scope = beta)).ranked()

        assertEquals(beta, ranked.best.game)
        assertNull(ranked.weakest)
        assertEquals(listOf(listOf(beta)), reads.map { it.second })
    }

    @Test
    fun `the window is the current week (from its Monday) for Week, the current month (from its first) for Month and All`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 1.0, "maya" to 2.0)))

        listOf(StatsRange.Week, StatsRange.Month, StatsRange.All).forEach { range ->
            reads.clear()
            provider(answers).settled(solved(range))
            assertEquals(
                "$range",
                listOf(if (range == StatsRange.Week) RankWindow.Week(LocalDate.of(2026, 11, 16)) else RankWindow.Month(LocalDate.of(2026, 11, 1))),
                reads.map { it.first }.distinct(),
            )
        }
    }

    @Test
    fun `signed out is one plain line asking to sign in, with no read`() = runTest {
        val section = provider(emptyMap(), viewer = null).settled(solved())

        assertEquals(StatsSection.VsFriends(SectionState.Ready(Locked(Unlock.SignIn))), section)
        assertEquals(emptyList<Any>(), reads)
    }

    @Test
    fun `a read that says signed out is the same line`() = runTest {
        val answers = mapOf(alpha to flowOf(OverallState.SignedOut), beta to flowOf(OverallState.SignedOut))

        assertEquals(StatsSection.VsFriends(SectionState.Ready(Locked(Unlock.SignIn))), provider(answers).settled(solved()))
    }

    @Test
    fun `nobody but the viewer on the boards is one plain line asking to add friends`() = runTest {
        val answers = mapOf(alpha to flowOf(standings("me" to 250.0)), beta to flowOf(standings("me" to 90.0)))

        assertEquals(StatsSection.VsFriends(SectionState.Ready(Locked(Unlock.AddFriends))), provider(answers).settled(solved()))
    }

    @Test
    fun `nobody on any board, or friends but not the viewer, leaves the section out`() = runTest {
        assertNull(provider(emptyMap()).settled(solved()))
        assertNull(provider(mapOf(alpha to flowOf(standings("maya" to 90.0)))).settled(solved()))
    }

    @Test
    fun `a range with no solve leaves the section out without reading`() = runTest {
        val input = f.input(f.facts(StatsRange.Week), StatsRange.Week, games)

        assertNull(provider(emptyMap()).settled(input))
        assertEquals(emptyList<Any>(), reads)
    }

    @Test
    fun `an offline, partial-forever, failed or hung read is Can't load right now`() = runTest {
        val friends = standings("me" to 1.0, "maya" to 2.0)
        val unavailable = StatsSection.VsFriends(SectionState.Unavailable)

        assertEquals(unavailable, provider(mapOf(alpha to flowOf(friends), beta to flowOf(OverallState.Offline(null)))).settled(solved()))
        assertEquals(unavailable, provider(mapOf(alpha to flow { throw IOException("down") })).settled(solved()))
        assertTrue(warnings.any { it.contains("failed") })
        assertEquals(unavailable, provider(mapOf(alpha to flow { awaitCancellation() }), timeout = 5.seconds).settled(solved()))
    }

    @Test
    fun `a half answer waits for the boards, a late complete one is read`() = runTest {
        val answers = mapOf(
            alpha to flowOf(OverallState.Loading, OverallState.Partial(OverallStandings(emptyList()), loaded = 1, total = 4), standings("me" to 5.0, "maya" to 6.0)),
        )

        assertEquals(alpha, provider(answers).settled(solved()).ranked().best.game)
    }
}
