package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The signed-in photo is the one source of the viewer's own row on every Ranks board; the leaderboard entries carry none. */
class RanksOwnPhotoTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val photo = "https://example.com/ann.png"

    private fun entry(uid: String, timeMs: Long) = LeaderboardEntry(uid, uid, "zip/$today/1", timeMs, 0, 1_700_000_000_000)

    private suspend fun hub(viewer: Identity) = RanksTestHub(
        FakeLeaderboardRepository(mapOf(LeaderboardRef("zip", today) to listOf(entry("maya", 60_000), entry("u1", 70_000)))),
        viewer,
    ).apply { befriend("maya") }

    private fun RanksViewModel.rows(): suspend () -> List<DailyBoardRowModel> = {
        (uiState.first { it.board.state is DailyBoardState.Ranked }.board.state as DailyBoardState.Ranked).rows
    }

    @Test
    fun `Overall shows the viewer's account photo on their row, others stay on the initial`() = runTest {
        val rows = hub(Identity("u1", "Ann", photo)).viewModel().rows()()

        assertEquals(photo, rows.single { it.isSelf }.photo)
        assertNull(rows.single { !it.isSelf }.photo)
    }

    @Test
    fun `a game's day, week and month boards in Global and Friends show the account photo on the viewer's row`() = runTest {
        val hub = hub(Identity("u1", "Ann", photo))

        for (scope in RankScope.entries) {
            for (window in RanksSelection.Window.entries) {
                val viewModel = hub.viewModel()
                viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(scope).setWindow(window) }
                assertEquals("$scope $window", photo, viewModel.rows()().single { it.isSelf }.photo)
            }
        }
    }

    @Test
    fun `an account with no photo leaves the initial`() = runTest {
        val viewModel = hub(Identity("u1", "Ann", null)).viewModel()
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)) }

        assertNull(viewModel.rows()().single { it.isSelf }.photo)
    }
}
