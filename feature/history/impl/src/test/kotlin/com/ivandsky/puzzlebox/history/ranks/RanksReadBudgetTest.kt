package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A23 at the view-model seam: reads = listeners (`observedBoards`) + fetches + counts over the
 * counting fake. Overall, the hub's one window-boards collection, is the only reader (goldfish B arch-2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RanksReadBudgetTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val games = listOf(ZIP, TANGO, QUEENS, GameId("strands"), GameId("minisudoku"))
    private val registry = TestGames.registry(games)

    private fun entry(game: GameId, uid: String, timeMs: Long) =
        LeaderboardEntry(uid, uid, "${game.value}/$today/1", timeMs, 0, 1_700_000_000_000)

    private val leaderboard = FakeLeaderboardRepository(
        games.associate { LeaderboardRef(it.value, today) to listOf(entry(it, "maya", 60_000), entry(it, "u1", 70_000)) },
    )

    private fun hub(identity: Identity? = Identity("u1", "Ann", null)) = RanksTestHub(leaderboard, identity, registry, today)

    private fun TestScope.collect(viewModel: RanksViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        runCurrent()
    }

    @Test
    fun `Overall Day costs one listener per game and nothing else`() = runTest {
        collect(hub().viewModel())

        assertEquals(games.map { LeaderboardRef(it.value, today) }, leaderboard.observedBoards)
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(0, leaderboard.countCalls)
    }

    @Test
    fun `a screen state change re-reads nothing`() = runTest {
        val viewModel = hub().viewModel()
        collect(viewModel)

        viewModel.select { it.toggleCalendar() }
        viewModel.select { it.setScope(RankScope.Friends) }
        runCurrent()

        assertEquals(5, leaderboard.observedBoards.size)
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(0, leaderboard.countCalls)
    }

    @Test
    fun `signed out reads nothing`() = runTest {
        collect(hub(identity = null).viewModel())

        assertEquals(0, leaderboard.observedBoards.size)
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(0, leaderboard.countCalls)
    }
}
