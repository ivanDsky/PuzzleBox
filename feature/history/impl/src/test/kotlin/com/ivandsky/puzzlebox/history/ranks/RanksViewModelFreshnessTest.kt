package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Ranks says when the LinkedIn part of a game's Friends day board was last read, and a pull asks LinkedIn again (backlog #1357). */
@OptIn(ExperimentalCoroutinesApi::class)
class RanksViewModelFreshnessTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val on = FakeReleaseFlags(ReleaseFlag.RanksFreshness)

    private fun hub() = RanksTestHub(
        FakeLeaderboardRepository(
            mapOf(LeaderboardRef("zip", today) to listOf(LeaderboardEntry("maya", "Maya", "zip/$today/1", 62_000, 0, 1_700_000_000_000))),
        ),
    )

    /** The phone's connected day with a board read [ago] before the hub's clock; never read when [ago] is null. */
    private fun RanksTestHub.read(ago: Duration?, link: LinkState = LinkState.Connected("member-1"), complete: Boolean = false) {
        linkedInDays.emit(
            ZIP, today,
            LinkedInDayData(
                link = link, delta = 0, boardInWindow = true, inFlight = false, lastSync = null,
                own = LinkedInOwnState.Unknown,
                ghosts = listOf(LinkedInGhost("ref-Cal", "Cal", 70_000, hintFree = true, flawless = false)),
                boardFetchedAtMs = ago?.let { now.toEpochMilli() - it.toMillis() },
                boardComplete = complete,
            ),
        )
    }

    private suspend fun RanksViewModel.zip(scope: RankScope, window: RanksSelection.Window = RanksSelection.Window.Day): RanksBoardSlice {
        select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(scope).setWindow(window) }
        return uiState.first {
            it.scope == scope && it.window == window && it.board.state is DailyBoardState.Ranked && it.board.title.contains("Zip")
        }.board
    }

    @Test
    fun `a game's Friends day board says how long ago LinkedIn was read`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }

        assertEquals("LinkedIn updated 5 min ago", h.viewModel(linkedIn = true, releaseFlags = on).zip(RankScope.Friends).freshness)
    }

    @Test
    fun `the line says final for a board read after its day`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5), complete = true) }

        val line = h.viewModel(linkedIn = true, releaseFlags = on).zip(RankScope.Friends).freshness

        assertEquals(true, line?.startsWith("LinkedIn final · read "))
    }

    @Test
    fun `a minute later the same line says 6 min`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }
        val viewModel = h.viewModel(linkedIn = true, releaseFlags = on)
        viewModel.zip(RankScope.Friends)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        h.advanceClock(Duration.ofMinutes(1))
        testScheduler.advanceTimeBy(60_000)
        runCurrent()

        assertEquals("LinkedIn updated 6 min ago", viewModel.uiState.value.board.freshness)
    }

    @Test
    fun `a day never read has no line`() = runTest {
        val h = hub().apply { befriend("maya"); read(null) }

        assertNull(h.viewModel(linkedIn = true, releaseFlags = on).zip(RankScope.Friends).freshness)
    }

    @Test
    fun `a link that is not connected has no line, the saved board being a relic`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5), link = LinkState.NotConnected) }

        assertNull(h.viewModel(linkedIn = true, releaseFlags = on).zip(RankScope.Friends).freshness)
    }

    @Test
    fun `Global and a week have no line`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }
        val viewModel = h.viewModel(linkedIn = true, releaseFlags = on)

        assertNull(viewModel.zip(RankScope.Global).freshness)
        assertNull(viewModel.zip(RankScope.Friends, RanksSelection.Window.Week).freshness)
    }

    @Test
    fun `with the flag off there is no line`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }

        assertNull(h.viewModel(linkedIn = true).zip(RankScope.Friends).freshness)
    }

    @Test
    fun `a pull asks LinkedIn for the shown game's Friends day once`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }
        val viewModel = h.viewModel(linkedIn = true, releaseFlags = on)
        viewModel.zip(RankScope.Friends)

        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(ZIP to today), h.linkedInDays.syncCalls)
    }

    @Test
    fun `a pull asks LinkedIn for the picked day, not today`() = runTest {
        val picked = today.minusDays(2)
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }
        val viewModel = h.viewModel(linkedIn = true, releaseFlags = on)
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(RankScope.Friends).pickFromCalendar(picked) }

        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(ZIP to picked), h.linkedInDays.syncCalls)
    }

    @Test
    fun `a pull with the flag off, on Overall, in Global or on a week asks LinkedIn nothing`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }
        val off = h.viewModel(linkedIn = true)
        off.zip(RankScope.Friends)
        off.refresh()

        val viewModel = h.viewModel(linkedIn = true, releaseFlags = on)
        viewModel.select { it.pickBoard(RanksBoard.Overall).setScope(RankScope.Friends) }
        viewModel.refresh()
        viewModel.zip(RankScope.Global)
        viewModel.refresh()
        viewModel.zip(RankScope.Friends, RanksSelection.Window.Week)
        viewModel.refresh()
        runCurrent()

        assertEquals(emptyList<Any>(), h.linkedInDays.syncCalls)
    }

    @Test
    fun `the pull indicator stays while LinkedIn is asked, then drops`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }
        val gate = CompletableDeferred<Unit>().also { h.linkedInDays.syncGate = it }
        val viewModel = h.viewModel(linkedIn = true, releaseFlags = on)
        viewModel.zip(RankScope.Friends)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.refreshing.collect { } }

        viewModel.refresh()
        runCurrent()
        testScheduler.advanceTimeBy(5_000)
        runCurrent()
        assertEquals(true, viewModel.refreshing.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(false, viewModel.refreshing.value)
    }

    @Test
    fun `a second pull while LinkedIn is still being asked sends no second sync`() = runTest {
        val h = hub().apply { befriend("maya"); read(Duration.ofMinutes(5)) }
        h.linkedInDays.syncGate = CompletableDeferred()
        val viewModel = h.viewModel(linkedIn = true, releaseFlags = on)
        viewModel.zip(RankScope.Friends)

        viewModel.refresh()
        viewModel.refresh()
        runCurrent()

        assertEquals(1, h.linkedInDays.syncCalls.size)
    }
}
