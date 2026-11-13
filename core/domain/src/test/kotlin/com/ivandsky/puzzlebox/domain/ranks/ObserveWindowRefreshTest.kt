package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.network.FakeNetworkStatus
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Edge, pull and the one backoff schedule, on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWindowRefreshTest {

    private val network = FakeNetworkStatus(initial = true)
    private val pulls = MutableSharedFlow<Unit>()
    private val boards = MutableStateFlow<RankWindowBoards>(RankWindowBoards.Boards(emptyList()))
    private val refresh = ObserveWindowRefresh(network)

    private val ref = LeaderboardRef("zip", LocalDate.of(2026, 11, 3))
    private val loaded = BoardLoad.Loaded(ref, RankOrder.TimeThenHints, BoardPage(emptyList(), 0), final = true)
    private val unavailable = BoardLoad.Unavailable(ref, RankOrder.TimeThenHints)

    private fun fail() { boards.value = RankWindowBoards.Boards(listOf(loaded, unavailable)) }
    private fun heal() { boards.value = RankWindowBoards.Boards(listOf(loaded)) }

    /** Virtual milliseconds at which each tick arrived. */
    private fun TestScope.ticks(): List<Long> {
        val at = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { refresh(pulls, boards).collect { at += testScheduler.currentTime } }
        runCurrent()
        return at
    }

    @Test
    fun `nothing failing and online from the start never ticks`() = runTest {
        val at = ticks()

        advanceTimeBy(10.minutes)

        assertEquals(emptyList<Long>(), at)
    }

    @Test
    fun `an offline to online edge ticks once`() = runTest {
        network.setOnline(false)
        val at = ticks()
        runCurrent()
        assertEquals(emptyList<Long>(), at)

        network.setOnline(true)
        runCurrent()

        assertEquals(listOf(0L), at)
    }

    @Test
    fun `going offline is not an edge`() = runTest {
        val at = ticks()

        network.setOnline(false)
        runCurrent()

        assertEquals(emptyList<Long>(), at)
    }

    @Test
    fun `a pull ticks at once even with nothing failing`() = runTest {
        val at = ticks()

        pulls.emit(Unit)
        runCurrent()

        assertEquals(listOf(0L), at)
    }

    @Test
    fun `failing boards back off as one doubling schedule up to the cap`() = runTest {
        val at = ticks()
        fail()

        advanceTimeBy(20.minutes)

        val s = 1_000L
        assertEquals(listOf(2 * s, 6 * s, 14 * s, 30 * s, 62 * s, 126 * s, 254 * s, 510 * s, 810 * s, 1_110 * s), at.take(10))
    }

    @Test
    fun `several failing boards share the one schedule`() = runTest {
        val other = BoardLoad.Unavailable(LeaderboardRef("tango", ref.day), RankOrder.TimeThenHints)
        val at = ticks()

        boards.value = RankWindowBoards.Boards(listOf(unavailable, other))
        advanceTimeBy(7.seconds)

        assertEquals(listOf(2_000L, 6_000L), at)
    }

    @Test
    fun `the schedule ends when every board has loaded`() = runTest {
        val at = ticks()
        fail()
        advanceTimeBy(3.seconds)

        heal()
        advanceTimeBy(20.minutes)

        assertEquals(listOf(2_000L), at)
    }

    @Test
    fun `a pull restarts the schedule from its first delay`() = runTest {
        val at = ticks()
        fail()
        advanceTimeBy(7.seconds)

        pulls.emit(Unit)
        advanceTimeBy(5.seconds)

        assertEquals(listOf(2_000L, 6_000L, 7_000L, 9_000L), at)
    }

    @Test
    fun `offline pauses the schedule and the edge ticks at once and restarts it`() = runTest {
        val at = ticks()
        fail()
        advanceTimeBy(3.seconds)

        network.setOnline(false)
        advanceTimeBy(10.minutes)
        assertEquals(listOf(2_000L), at)

        network.setOnline(true)
        runCurrent()
        val back = testScheduler.currentTime
        advanceTimeBy(3.seconds)

        assertEquals(listOf(2_000L, back, back + 2_000L), at)
    }

    @Test
    fun `signed out boards do not back off`() = runTest {
        val at = ticks()

        boards.value = RankWindowBoards.SignedOut
        advanceTimeBy(10.minutes)

        assertEquals(emptyList<Long>(), at)
    }

    // --- Through the window: the DoD ------------------------------------------------------

    @Test
    fun `one reconnect retries every unavailable final board in one cycle`() = runTest {
        val games = listOf(GameId("zip"), GameId("tango"), GameId("queens"))
        val day = LocalDate.of(2026, 11, 3)
        val leaderboard = FakeLeaderboardRepository()
        games.forEach { leaderboard.setEntries(LeaderboardRef(it.value, day), listOf(LeaderboardEntry("p1", "p1", "x/1/1", 1_000, 0, 1_700_000_000_000))) }
        leaderboard.readsAvailable = false
        val window = ObserveRankWindow(
            FakeIdentityService(initial = Identity("u1", "Ann", null)),
            leaderboard,
            FakeFinalBoardStore(),
            GameRegistry(games.map { TestGames.plugin(it) }),
            FakeGameCalendar(LocalDate.of(2026, 11, 13)),
            Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC),
        )
        network.setOnline(false)
        val seen = MutableStateFlow<RankWindowBoards>(RankWindowBoards.Boards(emptyList()))
        val ticks = refresh(pulls, seen)
        var latest: RankWindowBoards = RankWindowBoards.SignedOut
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            window(RankWindow.Day(day), games, ticks).collect { latest = it; seen.value = it }
        }
        runCurrent()
        assertEquals("one attach read per board", 3, leaderboard.fetchCalls)

        leaderboard.readsAvailable = true
        network.setOnline(true)
        runCurrent()

        assertEquals("one retry per board, no more", 6, leaderboard.fetchCalls)
        assertEquals(3, (latest as RankWindowBoards.Boards).boards.count { it is BoardLoad.Loaded })

        advanceTimeBy(10.minutes)
        assertEquals("nothing failing, nothing more read", 6, leaderboard.fetchCalls)
    }
}
