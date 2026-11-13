package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.gameui.board.BoardRowValue
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The hub's state over fakes: Overall on landing (ticket 14), a game's own board once one is picked (ticket 02). */
class RanksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)

    private fun entry(game: GameId, uid: String, name: String, timeMs: Long) =
        LeaderboardEntry(uid, name, "${game.value}/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun hub(
        boards: Map<LeaderboardRef, List<LeaderboardEntry>> = emptyMap(),
        identity: Identity? = Identity("u1", "Ann", null),
    ) = RanksTestHub(FakeLeaderboardRepository(boards), identity)

    private fun ref(game: GameId) = LeaderboardRef(game.value, today)

    private val zipBoard = ref(ZIP) to listOf(entry(ZIP, "maya", "Maya", 62_000), entry(ZIP, "u1", "Ann", 75_000))
    private val tangoBoard = ref(TANGO) to listOf(entry(TANGO, "jonas", "Jonas", 40_000))
    private val queensBoard = ref(QUEENS) to listOf(entry(QUEENS, "u1", "Ann", 50_000), entry(QUEENS, "maya", "Maya", 90_000))

    private suspend fun RanksViewModel.ranked(): RanksBoardSlice =
        uiState.first { it.board.state is DailyBoardState.Ranked }.board

    @Test
    fun `lands on Overall, Loading until the first answer`() = runTest {
        val viewModel = hub().viewModel()

        assertEquals(RanksSelection(), viewModel.selection.value)
        assertEquals(DailyBoardState.Loading, viewModel.uiState.value.board.state)
        assertEquals("Overall · today", viewModel.uiState.value.board.title)
    }

    @Test
    fun `Overall ranks the viewer by their points and says how many boards are in`() = runTest {
        val slice = hub(mapOf(zipBoard)).viewModel().ranked()

        assertEquals("Overall · today", slice.title)
        val rows = (slice.state as DailyBoardState.Ranked).rows
        assertEquals(listOf("Maya", "Ann"), rows.map { it.name })
        assertEquals(listOf(1, 2), rows.map { it.rank })
        assertEquals(listOf(false, true), rows.map { it.isSelf })
        assertEquals(BoardRowValue.Points(50.0, 1, atLeast = false), rows[1].scoreValue)
        assertEquals("1 of 3 boards", slice.note)
    }

    @Test
    fun `Overall with every board in has no partial note`() = runTest {
        val slice = hub(mapOf(zipBoard, tangoBoard, queensBoard)).viewModel().ranked()

        assertNull(slice.note)
        val rows = (slice.state as DailyBoardState.Ranked).rows
        assertEquals(listOf("Maya", "Ann", "Jonas"), rows.map { it.name })
        assertEquals(listOf(1, 1, 3), rows.map { it.rank })
        assertEquals(BoardRowValue.Points(150.0, 2, atLeast = false), rows.single { it.isSelf }.scoreValue)
    }

    @Test
    fun `with no board readable Overall is unavailable, not an error`() = runTest {
        val slice = hub().viewModel().uiState.first { it.board.state != DailyBoardState.Loading }.board

        assertEquals(DailyBoardState.Unavailable, slice.state)
    }

    @Test
    fun `signed out shows the sign-in state and signing in loads the boards`() = runTest {
        val hub = hub(mapOf(zipBoard), identity = null)
        val viewModel = hub.viewModel()

        assertEquals(DailyBoardState.SignedOut, viewModel.uiState.first { it.board.state != DailyBoardState.Loading }.board.state)

        hub.identity.currentIdentity = Identity("u1", "Ann", null)

        assertEquals(listOf("Maya", "Ann"), (viewModel.ranked().state as DailyBoardState.Ranked).rows.map { it.name })
    }

    @Test
    fun `picking a game shows that game's board for today`() = runTest {
        val viewModel = hub(mapOf(zipBoard, tangoBoard)).viewModel()
        viewModel.ranked()

        viewModel.select { it.pickBoard(RanksBoard.Game(TANGO)) }
        val slice = viewModel.uiState.first { it.board.title == "Tango · today" && it.board.state is DailyBoardState.Ranked }.board

        assertEquals(listOf("Jonas"), (slice.state as DailyBoardState.Ranked).rows.map { it.name })
        assertNull(slice.note)
    }

    @Test
    fun `reset returns to Overall`() = runTest {
        val viewModel = hub(mapOf(zipBoard, tangoBoard)).viewModel()
        viewModel.select { it.pickBoard(RanksBoard.Game(TANGO)) }
        viewModel.select { it.reset() }

        assertEquals("Overall · today", viewModel.uiState.first { it.board.state is DailyBoardState.Ranked }.board.title)
    }

    @Test
    fun `select applies the selection's own transition`() = runTest {
        val viewModel = hub().viewModel()

        viewModel.select { it.toggleCalendar() }

        assertTrue(viewModel.selection.value.calendarExpanded)
    }
}
