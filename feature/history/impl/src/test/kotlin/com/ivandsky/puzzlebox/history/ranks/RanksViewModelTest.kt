package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.gameui.board.BoardRowValue
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryTestSupport
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
    fun `selecting each board emits its ranked rows, keyed by the same uids so the list re-ranks`() = runTest {
        val viewModel = hub(mapOf(zipBoard, tangoBoard, queensBoard)).viewModel()
        fun rowsOf(slice: RanksBoardSlice) = (slice.state as DailyBoardState.Ranked).rows

        val overall = rowsOf(viewModel.ranked())
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)) }
        val zip = rowsOf(viewModel.uiState.first { it.board.title == "Zip · today" && it.board.state is DailyBoardState.Ranked }.board)
        viewModel.select { it.pickBoard(RanksBoard.Game(QUEENS)) }
        val queens = rowsOf(viewModel.uiState.first { it.board.title == "Queens · today" && it.board.state is DailyBoardState.Ranked }.board)

        assertEquals(listOf("maya", "u1", "jonas"), overall.map { it.playerId })
        assertEquals(listOf("maya", "u1"), zip.map { it.playerId })
        assertEquals(listOf("u1", "maya"), queens.map { it.playerId })
        assertEquals(listOf(BoardRowValue.Timed(62_000, 0), BoardRowValue.Timed(75_000, 0)), zip.map { it.scoreValue })
        assertEquals(listOf(false, true), zip.map { it.isSelf })
    }

    @Test
    fun `a swipe walks the boards in order and stops at either end`() = runTest {
        val viewModel = hub(mapOf(zipBoard, tangoBoard, queensBoard)).viewModel()
        val order = rankBoards(hub().registry)

        viewModel.swipe(-1)
        assertEquals(RanksBoard.Overall, viewModel.selection.value.board)

        order.drop(1).forEach {
            viewModel.swipe(1)
            assertEquals(it, viewModel.selection.value.board)
        }
        viewModel.swipe(1)
        assertEquals(order.last(), viewModel.selection.value.board)
    }

    @Test
    fun `the state says where the swipe stands`() = runTest {
        val viewModel = hub(mapOf(zipBoard)).viewModel()
        val count = rankBoards(hub().registry).size
        viewModel.ranked()

        assertEquals(0 to count, viewModel.uiState.value.let { it.position to it.boardCount })

        viewModel.swipe(1)
        assertEquals(1, viewModel.uiState.first { it.position == 1 }.position)
    }

    @Test
    fun `reset returns to Overall`() = runTest {
        val viewModel = hub(mapOf(zipBoard, tangoBoard)).viewModel()
        viewModel.select { it.pickBoard(RanksBoard.Game(TANGO)) }
        viewModel.select { it.reset(RankScope.Global) }

        assertEquals("Overall · today", viewModel.uiState.first { it.board.state is DailyBoardState.Ranked }.board.title)
    }

    @Test
    fun `select applies the selection's own transition`() = runTest {
        val viewModel = hub().viewModel()

        viewModel.select { it.toggleCalendar() }

        assertTrue(viewModel.selection.value.calendarExpanded)
    }

    @Test
    fun `picking a day re-targets the board to that day and folds the calendar to its week`() = runTest {
        val yesterday = today.minusDays(1)
        val maya = entry(ZIP, "maya", "Maya", 58_000)
        val h = hub(mapOf(LeaderboardRef(ZIP.value, yesterday) to listOf(maya)))
        h.daily.setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(yesterday)))
        val viewModel = h.viewModel()
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).toggleCalendar() }

        viewModel.selectDay(yesterday)
        val state = viewModel.uiState.first { it.board.state is DailyBoardState.Ranked && it.calendar.selectedDay == yesterday }

        assertEquals("Zip · Nov 12", state.board.title)
        assertEquals(listOf("maya"), (state.board.state as DailyBoardState.Ranked).rows.map { it.playerId })
        assertEquals(yesterday, viewModel.selection.value.day)
        assertEquals(false, state.calendar.expanded)
        assertEquals(yesterday, state.calendar.shownDay)
    }

    @Test
    fun `signed out the strip, month, stats and Your solve come from local data`() = runTest {
        val yesterday = today.minusDays(1)
        val h = hub(identity = null)
        h.daily.setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(yesterday)))
        h.completions.setRecords(listOf(HistoryTestSupport.record(yesterday, timeMs = 95_000, hints = 2)))
        val viewModel = h.viewModel()
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).toggleCalendar() }

        viewModel.selectDay(yesterday)
        val state = viewModel.uiState.first { it.stats != null && it.yourSolve != null }

        assertEquals(DailyBoardState.SignedOut, state.board.state)
        assertEquals(setOf(today, yesterday), state.calendar.daysWithRows)
        assertEquals(yesterday, state.calendar.earliestDay)
        assertEquals(1, state.stats?.solved)
        assertEquals(95_000L, state.yourSolve?.row?.record?.time)
        assertEquals(2, state.yourSolve?.row?.record?.hints)
    }

    @Test
    fun `Your solve belongs to a game's board on a day you solved it, not Overall`() = runTest {
        val h = hub()
        h.daily.setHistory(ZIP, listOf(HistoryTestSupport.entry(today)))
        h.completions.setRecords(listOf(HistoryTestSupport.record(today)))
        val viewModel = h.viewModel()

        val overall = viewModel.uiState.first { it.stats != null }
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)) }
        val zip = viewModel.uiState.first { it.yourSolve != null }
        viewModel.select { it.pickBoard(RanksBoard.Game(TANGO)) }
        val tango = viewModel.uiState.first { it.board.title.startsWith("Tango") }

        assertNull(overall.yourSolve)
        assertEquals(ZIP, zip.yourSolve?.row?.gameId)
        assertNull(tango.yourSolve)
    }

    @Test
    fun `the calendar pages by week and month without moving the picked day`() = runTest {
        val h = hub()
        h.daily.setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(today.minusDays(20))))
        val viewModel = h.viewModel()
        viewModel.uiState.first { it.calendar.earliestDay != null }

        viewModel.showCalendar(today.minusDays(14), expanded = false)
        val state = viewModel.uiState.first { it.calendar.shownDay == today.minusDays(14) }

        assertNull(viewModel.selection.value.day)
        assertEquals(today, state.calendar.selectedDay)
    }

    private fun rowsOf(slice: RanksBoardSlice) = (slice.state as? DailyBoardState.Ranked)?.rows.orEmpty()

    private fun placed(slice: RanksBoardSlice) = rowsOf(slice).map { it.name to it.rank }

    private fun RanksUiState.standingOn(name: String) = places.single { it.name == name }.standing

    @Test
    fun `Friends ranks Overall among the viewer and friends, points over the friends who played, and Global is as it was`() = runTest {
        val h = hub(mapOf(zipBoard, tangoBoard, queensBoard))
        h.befriend("jonas")
        val viewModel = h.viewModel()
        assertEquals(listOf("Maya" to 1, "Ann" to 1, "Jonas" to 3), placed(viewModel.ranked()))

        viewModel.setScope(RankScope.Friends)
        val friends = viewModel.uiState.first { it.scope == RankScope.Friends && rowsOf(it.board).size == 2 }

        // Among friends Ann is alone on Zip and Queens (100 each) and Jonas alone on Tango (100).
        val rows = rowsOf(friends.board)
        assertEquals(listOf("Ann" to 1, "Jonas" to 2), placed(friends.board))
        assertEquals(BoardRowValue.Points(200.0, 2, atLeast = false), rows[0].scoreValue)
        assertEquals(BoardRowValue.Points(100.0, 1, atLeast = false), rows[1].scoreValue)
        assertEquals(Standing.Place(1), friends.standingOn("Overall"))

        viewModel.setScope(RankScope.Global)
        val global = viewModel.uiState.first { it.scope == RankScope.Global && rowsOf(it.board).size == 3 }
        assertEquals(listOf("Maya" to 1, "Ann" to 1, "Jonas" to 3), placed(global.board))
        assertEquals(BoardRowValue.Points(150.0, 2, atLeast = false), rowsOf(global.board).single { it.isSelf }.scoreValue)
    }

    @Test
    fun `Friends numbers a game's rows among the viewer and friends, and Global brings everyone back`() = runTest {
        val everyone = listOf(entry(ZIP, "zed", "Zed", 50_000), entry(ZIP, "maya", "Maya", 62_000), entry(ZIP, "yan", "Yan", 70_000), entry(ZIP, "u1", "Ann", 75_000))
        val h = hub(mapOf(ref(ZIP) to everyone))
        h.befriend("maya")
        val viewModel = h.viewModel()
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)) }
        assertEquals(listOf("Zed" to 1, "Maya" to 2, "Yan" to 3, "Ann" to 4), placed(viewModel.ranked()))

        viewModel.setScope(RankScope.Friends)
        val friends = viewModel.uiState.first { it.scope == RankScope.Friends && rowsOf(it.board).size == 2 }
        assertEquals(listOf("Maya" to 1, "Ann" to 2), placed(friends.board))
        assertEquals(Standing.Place(2), friends.standingOn("Zip"))

        viewModel.setScope(RankScope.Global)
        val global = viewModel.uiState.first { it.scope == RankScope.Global && rowsOf(it.board).size == 4 }
        assertEquals(listOf("Zed" to 1, "Maya" to 2, "Yan" to 3, "Ann" to 4), placed(global.board))
        assertEquals(Standing.Place(4), global.standingOn("Zip"))
    }

    @Test
    fun `Friends with no friend on the board is the viewer alone at first`() = runTest {
        val viewModel = hub(mapOf(zipBoard)).viewModel()
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(RankScope.Friends) }

        val alone = viewModel.uiState.first { it.scope == RankScope.Friends && it.board.state is DailyBoardState.Ranked }

        assertEquals(listOf("Ann" to 1), placed(alone.board))
        assertEquals(Standing.Place(1), alone.standingOn("Zip"))
    }

    @Test
    fun `Week and Month rank a game by average over the days played, titled for the window`() = runTest {
        val viewModel = hub(mapOf(zipBoard)).viewModel()
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)) }

        viewModel.setWindow(RanksSelection.Window.Week)
        val week = viewModel.uiState.first { it.window == RanksSelection.Window.Week && it.board.state is DailyBoardState.Ranked }
        assertEquals("Zip · this week", week.board.title)
        assertTrue(rowsOf(week.board).all { it.scoreValue is BoardRowValue.Average })

        viewModel.setWindow(RanksSelection.Window.Month)
        viewModel.selectDay(today.minusDays(3))
        val month = viewModel.uiState.first { it.window == RanksSelection.Window.Month && it.board.title == "Zip · November 2026" }
        assertEquals(RanksSelection.Window.Month, viewModel.selection.value.window)
        assertEquals(today.minusDays(3), viewModel.selection.value.day)
        assertEquals(RankScope.Global, month.scope)
    }
}
