package com.ivandsky.puzzlebox.insights.domain.stats

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStanding.Kept
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStanding.Locked
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rank history section: kept weeks per game, summed on the all-games page, ignoring the range, and every way a page without kept weeks can end. */
class RankHistoryStatsSectionProviderTest {

    private val f = StatsSeriesFixture()
    private val reads = mutableListOf<Pair<RankWindow, List<GameId>>>()
    private val warnings = mutableListOf<String>()
    private val games = listOf(alpha, beta)

    private fun kept(game: GameId, weekStart: String, points: Double, owner: String = "me") =
        RankHistoryStore.Week(owner, game, LocalDate.parse(weekStart), points, friendCount = 2)

    private fun standings(vararg uids: String) = OverallState.Complete(
        OverallStandings(uids.mapIndexed { index, uid -> OverallStandings.Row(uid, uid, index + 1, 100.0 - index, games = 5, truncated = false) }),
    )

    private fun friends(state: OverallState): (RankWindow, List<GameId>) -> Flow<OverallState> = { window, read ->
        reads += window to read
        flowOf(state)
    }

    private suspend fun input(range: StatsRange = StatsRange.Month, scope: GameId? = null) = f.input(f.facts(range, scope), range, games)

    private suspend fun StatsSectionProvider.settled(input: StatsInput): StatsSection.RankHistory {
        var last: StatsSection? = null
        section(input).test {
            last = awaitItem()
            if ((last as? StatsSection.RankHistory)?.state == SectionState.Loading) last = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return last as StatsSection.RankHistory
    }

    private fun StatsSection.RankHistory.ready() = (state as SectionState.Ready).value

    @Test
    fun `a single game's page is that game's kept weeks, oldest first, as kept`() = runTest {
        val provider = f.rankHistory(
            kept = listOf(kept(alpha, "2026-11-09", 70.5), kept(alpha, "2026-11-02", 60.0), kept(beta, "2026-11-09", 5.0), kept(alpha, "2026-11-16", 1.0, owner = "other")),
            friendsOverall = friends(standings("me", "maya")),
        )

        val section = provider.settled(input(scope = alpha))

        assertEquals(
            Kept(listOf(Kept.Week(LocalDate.of(2026, 11, 2), 60.0), Kept.Week(LocalDate.of(2026, 11, 9), 70.5))),
            section.ready(),
        )
        assertEquals("kept weeks are local: no friends read", emptyList<Any>(), reads)
    }

    @Test
    fun `the all-games page adds the games' points per day for each week`() = runTest {
        val provider = f.rankHistory(
            kept = listOf(kept(alpha, "2026-11-02", 60.0), kept(beta, "2026-11-02", 15.0), kept(beta, "2026-11-09", 40.0)),
        )

        val section = provider.settled(input())

        assertEquals(
            Kept(listOf(Kept.Week(LocalDate.of(2026, 11, 2), 75.0), Kept.Week(LocalDate.of(2026, 11, 9), 40.0))),
            section.ready(),
        )
    }

    @Test
    fun `a line needs 2 weeks, one week is a number`() {
        val week = Kept.Week(LocalDate.of(2026, 11, 2), 10.0)

        assertFalse(Kept(emptyList()).isLine)
        assertFalse(Kept(listOf(week)).isLine)
        assertTrue(Kept(listOf(week, week.copy(start = LocalDate.of(2026, 11, 9)))).isLine)
    }

    @Test
    fun `the range does not change what it asks nor its answer`() = runTest {
        val provider = f.rankHistory(kept = listOf(kept(alpha, "2026-11-02", 60.0), kept(beta, "2026-11-02", 15.0)))
        val week = input(StatsRange.Week)
        val month = input(StatsRange.Month)
        val all = input(StatsRange.All)

        assertEquals(provider.readKey(week), provider.readKey(month))
        assertEquals(provider.readKey(month), provider.readKey(all))
        assertEquals(provider.settled(week), provider.settled(all))
    }

    @Test
    fun `a single game's page asks for that game, and a new solve does not change what it asks`() = runTest {
        val provider = f.rankHistory()
        val before = input(scope = alpha)
        f.history.store(f.history.solve(alpha, f.day(18)))
        val after = input(scope = alpha)

        assertNotEquals(provider.readKey(input()), provider.readKey(before))
        assertEquals(provider.readKey(before), provider.readKey(after))
    }

    @Test
    fun `signed out is one plain line asking to sign in, with no read`() = runTest {
        val section = f.rankHistory(viewer = null, friendsOverall = friends(standings("me", "maya"))).settled(input())

        assertEquals(Locked(Unlock.SignIn), section.ready())
        assertEquals(emptyList<Any>(), reads)
    }

    @Test
    fun `nothing kept and friends on the boards is no weeks yet, after loading`() = runTest {
        val provider = f.rankHistory(friendsOverall = friends(standings("me", "maya")))

        provider.section(input()).test {
            assertEquals(StatsSection.RankHistory(SectionState.Loading), awaitItem())
            assertEquals(StatsSection.RankHistory(SectionState.Ready(Kept(emptyList()))), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(RankWindow.Week(LocalDate.of(2026, 11, 16))), reads.map { it.first }.distinct())
    }

    @Test
    fun `nothing kept and nobody on the boards but the viewer is one plain line asking to add friends`() = runTest {
        val section = f.rankHistory(friendsOverall = friends(standings("me"))).settled(input())

        assertEquals(Locked(Unlock.AddFriends), section.ready())
    }

    @Test
    fun `nothing kept and no board at all is no weeks yet`() = runTest {
        val section = f.rankHistory(friendsOverall = friends(OverallState.Empty)).settled(input())

        assertEquals(Kept(emptyList()), section.ready())
    }

    @Test
    fun `nothing kept and offline is Can't load right now`() = runTest {
        val section = f.rankHistory(friendsOverall = friends(OverallState.Offline(null))).settled(input())

        assertEquals(SectionState.Unavailable, section.state)
    }

    @Test
    fun `nothing kept and a partial read is Can't load right now`() = runTest {
        val partial = OverallState.Partial(OverallStandings(emptyList()), loaded = 1, total = 2)

        assertEquals(SectionState.Unavailable, f.rankHistory(friendsOverall = friends(partial)).settled(input()).state)
    }

    @Test
    fun `nothing kept and a read that fails or never ends is Can't load right now, and logged once for the failure`() = runTest {
        val failing = f.rankHistory(warnings = warnings) { _, _ -> flow { throw IOException("down") } }
        val hanging = f.rankHistory(timeout = 1.seconds) { _, _ -> flow { awaitCancellation() } }

        assertEquals(SectionState.Unavailable, failing.settled(input()).state)
        assertEquals(SectionState.Unavailable, hanging.settled(input()).state)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `kept weeks show even when the network is offline`() = runTest {
        val provider = f.rankHistory(kept = listOf(kept(alpha, "2026-11-02", 60.0)), friendsOverall = friends(OverallState.Offline(null)))

        assertEquals(Kept(listOf(Kept.Week(LocalDate.of(2026, 11, 2), 60.0))), provider.settled(input(scope = alpha)).ready())
        assertEquals(emptyList<Any>(), reads)
    }

    @Test
    fun `weeks kept for another account are not shown`() = runTest {
        val provider = f.rankHistory(
            viewer = Identity("someone", "Someone", null),
            kept = listOf(kept(alpha, "2026-11-02", 60.0)),
            friendsOverall = friends(standings("someone")),
        )

        assertEquals(Locked(Unlock.AddFriends), provider.settled(input(scope = alpha)).ready())
    }
}
