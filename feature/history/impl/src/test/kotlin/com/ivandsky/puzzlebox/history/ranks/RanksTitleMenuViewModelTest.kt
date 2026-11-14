package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The title menu's places and the one-time swipe nudge at the view-model seam (ticket 16). */
class RanksTitleMenuViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)

    private fun entry(game: GameId, uid: String, name: String, timeMs: Long) =
        LeaderboardEntry(uid, name, "${game.value}/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun ref(game: GameId) = LeaderboardRef(game.value, today)

    private val boards = mapOf(
        ref(ZIP) to listOf(entry(ZIP, "maya", "Maya", 62_000), entry(ZIP, "u1", "Ann", 75_000)),
        ref(TANGO) to listOf(entry(TANGO, "jonas", "Jonas", 40_000)),
        ref(QUEENS) to listOf(entry(QUEENS, "u1", "Ann", 50_000), entry(QUEENS, "maya", "Maya", 90_000)),
    )

    private fun hub(identity: Identity? = Identity("u1", "Ann", null)) = RanksTestHub(FakeLeaderboardRepository(boards), identity)

    private suspend fun RanksViewModel.settled(): RanksUiState = uiState.first { it.board.state != DailyBoardState.Loading && it.places.isNotEmpty() }

    private fun RanksUiState.standings() = places.associate { it.name to it.standing }

    @Test
    fun `the menu lists every board in swipe order with the viewer's place on each`() = runTest {
        val h = hub()
        val state = h.viewModel().uiState.first { state -> state.places.any { it.standing is Standing.Place } }

        assertEquals(rankBoards(h.registry), state.places.map { it.board })
        assertEquals(listOf("Overall", "Zip", "Tango", "Queens"), state.places.map { it.name })
        assertEquals(
            mapOf(
                "Overall" to Standing.Place(1),
                "Zip" to Standing.Place(2),
                "Tango" to Standing.None,
                "Queens" to Standing.Place(1),
            ),
            state.standings(),
        )
    }

    @Test
    fun `signed out the menu has boards and no places`() = runTest {
        val state = hub(identity = null).viewModel().settled()

        assertEquals(4, state.places.size)
        assertTrue(state.places.none { it.standing is Standing.Place })
    }

    @Test
    fun `the places do not depend on which board is showing, and follow the scope`() = runTest {
        val viewModel = hub().viewModel()
        val landed = viewModel.uiState.first { state -> state.places.any { it.standing is Standing.Place } }.standings()

        viewModel.pickBoard(RanksBoard.Game(QUEENS))
        assertEquals(landed, viewModel.uiState.first { it.board.title.startsWith("Queens") && it.board.state is DailyBoardState.Ranked }.standings())

        // F18: with no friends the viewer is first wherever they played, in Overall too.
        viewModel.setScope(RankScope.Friends)
        val friends = viewModel.uiState.first { state -> state.scope == RankScope.Friends && state.places.none { it.standing == Standing.Pending } }

        assertEquals(
            mapOf("Overall" to Standing.Place(1), "Zip" to Standing.Place(1), "Tango" to Standing.None, "Queens" to Standing.Place(1)),
            friends.standings(),
        )
    }

    @Test
    fun `picking a board from the menu shows its board`() = runTest {
        val viewModel = hub().viewModel()

        viewModel.pickBoard(RanksBoard.Game(TANGO))

        assertEquals(RanksBoard.Game(TANGO), viewModel.selection.value.board)
        assertEquals(2, viewModel.uiState.first { it.position == 2 }.position)
    }

    @Test
    fun `the swipe nudge shows on the first open only`() = runTest {
        val h = hub()
        val first = h.viewModel()

        assertTrue(first.uiState.first { it.places.isNotEmpty() }.nudge)

        first.markSwipeNudgeSeen()
        assertFalse(first.uiState.first { !it.nudge }.nudge)

        assertFalse(h.viewModel().uiState.first { it.places.isNotEmpty() }.nudge)
    }

    @Test
    fun `the nudge is remembered under its own non-game id`() = runTest {
        val h = hub()
        val viewModel = h.viewModel()
        viewModel.uiState.first { it.nudge }

        viewModel.markSwipeNudgeSeen()
        viewModel.uiState.first { !it.nudge }

        assertEquals(setOf(GameId("ranks.swipe")), h.preferences.seen)
    }

    @Test
    fun `swiping teaches the gesture, so the nudge is spent`() = runTest {
        val h = hub()
        val viewModel = h.viewModel()
        viewModel.uiState.first { it.nudge }

        viewModel.swipe(1)

        assertFalse(viewModel.uiState.first { !it.nudge }.nudge)
    }
}
