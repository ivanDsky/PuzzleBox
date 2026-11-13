package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The hub's state over fakes: the board slot shows today's board of the first listed game (ticket 02). */
class RanksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val ann = Identity("u1", "Ann", null)

    private fun entry(game: GameId, uid: String, timeMs: Long) =
        LeaderboardEntry(uid, uid.replaceFirstChar(Char::uppercase), "${game.value}/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun viewModel(
        boards: Map<LeaderboardRef, List<LeaderboardEntry>> = emptyMap(),
        identity: Identity? = ann,
        registry: GameRegistry = HistoryTestSupport.registry,
    ) = RanksViewModel(
        ObserveLeaderboard(
            FakeIdentityService(initial = identity),
            FakeLeaderboardRepository(boards),
            registry,
            FakeGameCalendar(today),
        ),
        registry,
    )

    private val zipBoard = LeaderboardRef(ZIP.value, today) to listOf(entry(ZIP, "maya", 62_000), entry(ZIP, "ann", 75_000))
    private val tangoBoard = LeaderboardRef(TANGO.value, today) to listOf(entry(TANGO, "jonas", 40_000))

    private suspend fun RanksViewModel.ranked(): RanksBoardSlice =
        uiState.first { it.board.state is DailyBoardState.Ranked }.board

    @Test
    fun `emits a Ranked board for today's first registry game`() = runTest {
        val slice = viewModel(mapOf(zipBoard, tangoBoard)).ranked()

        assertEquals("Zip · today", slice.title)
        val ranked = slice.state as DailyBoardState.Ranked
        assertEquals(listOf("Maya", "Ann"), ranked.rows.map { it.name })
        assertEquals(listOf(1, 2), ranked.rows.map { it.rank })
    }

    @Test
    fun `Overall is selected at the start and still shows the first game's board`() = runTest {
        val viewModel = viewModel(mapOf(zipBoard))

        assertEquals(RanksSelection(), viewModel.selection.value)
        assertEquals("Zip · today", viewModel.ranked().title)
    }

    @Test
    fun `the board is Loading until the first snapshot`() = runTest {
        val viewModel = viewModel()

        assertEquals(DailyBoardState.Loading, viewModel.uiState.value.board.state)
        assertEquals("Zip · today", viewModel.uiState.value.board.title)
    }

    @Test
    fun `a game with no board is Unavailable, not an error`() = runTest {
        val slice = viewModel().uiState.first { it.board.state != DailyBoardState.Loading }.board

        assertEquals(DailyBoardState.Unavailable, slice.state)
    }

    @Test
    fun `signed out shows the signed-out state`() = runTest {
        val slice = viewModel(mapOf(zipBoard), identity = null).uiState.first { it.board.state != DailyBoardState.Loading }.board

        assertEquals(DailyBoardState.SignedOut, slice.state)
    }

    @Test
    fun `picking a game retargets the board to it`() = runTest {
        val viewModel = viewModel(mapOf(zipBoard, tangoBoard))
        viewModel.ranked()

        viewModel.select { it.pickBoard(RanksBoard.Game(TANGO)) }
        val slice = viewModel.uiState.first { it.board.title == "Tango · today" && it.board.state is DailyBoardState.Ranked }.board

        assertEquals(listOf("Jonas"), (slice.state as DailyBoardState.Ranked).rows.map { it.name })
    }

    @Test
    fun `reset returns the board to the first game`() = runTest {
        val viewModel = viewModel(mapOf(zipBoard, tangoBoard))
        viewModel.select { it.pickBoard(RanksBoard.Game(TANGO)) }
        viewModel.select { it.reset() }

        assertEquals("Zip · today", viewModel.uiState.first { it.board.state is DailyBoardState.Ranked }.board.title)
    }

    @Test
    fun `select applies the selection's own transition`() = runTest {
        val viewModel = viewModel()

        viewModel.select { it.toggleCalendar() }

        assertTrue(viewModel.selection.value.calendarExpanded)
    }
}
