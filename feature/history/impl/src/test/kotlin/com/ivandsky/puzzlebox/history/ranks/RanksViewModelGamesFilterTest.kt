package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.flags.HistoryFlag
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Games
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.board.BoardRowValue
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
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

/**
 * GamesFilter (#1707): the Games pick (All games or LinkedIn games) re-counts Overall and re-lists the boards over the
 * boards the hub already read. Strands is the one game LinkedIn does not feed; Zip, Tango and Queens are LinkedIn games.
 */
class RanksViewModelGamesFilterTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val strands = GameId("strands")
    private val registry = TestGames.registry(listOf(ZIP, TANGO, QUEENS, strands))

    private fun entry(game: GameId, uid: String, timeMs: Long) =
        LeaderboardEntry(uid, uid, "${game.value}/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun ref(game: GameId, day: LocalDate = today) = LeaderboardRef(game.value, day)

    // Two players on every board but Tango, so a place is worth 100 or 50: maya and u1 each play Zip, Queens and Strands.
    private val zip = ref(ZIP) to listOf(entry(ZIP, "maya", 62_000), entry(ZIP, "u1", 75_000))
    private val tango = ref(TANGO) to listOf(entry(TANGO, "jonas", 40_000))
    private val queens = ref(QUEENS) to listOf(entry(QUEENS, "u1", 50_000), entry(QUEENS, "maya", 90_000))
    private val strandsBoard = ref(strands) to listOf(entry(strands, "u1", 30_000), entry(strands, "maya", 40_000))

    private fun hub(vararg boards: Pair<LeaderboardRef, List<LeaderboardEntry>>, linkedInDefault: Boolean = false) =
        RanksTestHub(FakeLeaderboardRepository(boards.toMap()), registry = registry, linkedInDefault = linkedInDefault).also {
            listOf(ZIP, TANGO, QUEENS).forEachIndexed { at, game -> it.daily.setLinkedInGameType(game, 100 + at) }
        }

    private fun everyBoard() = arrayOf(zip, tango, queens, strandsBoard)

    private fun rowsOf(slice: RanksBoardSlice) = (slice.state as DailyBoardState.Ranked).rows

    private fun totals(slice: RanksBoardSlice) = rowsOf(slice).map { it.playerId to (it.scoreValue as BoardRowValue.Points).total }

    /** The hub's board under [games] in [scope] and [window] once it is Ranked, whichever board is open (Overall unless a test picked a game). */
    private suspend fun RanksViewModel.overall(games: Games, scope: RankScope = RankScope.Global, window: Window = Window.Day): RanksUiState {
        select { it.setScope(scope).setWindow(window) }
        setGames(games)
        val span = when (window) {
            Window.Day -> "today"
            Window.Week -> "this week"
            Window.Month -> "this month"
        }
        return uiState.first { it.games == games && it.scope == scope && it.board.title.endsWith(span) && it.board.state is DailyBoardState.Ranked }
    }

    @Test
    fun `LinkedIn games sums Overall over the LinkedIn games only and Strands leaves the list`() = runTest {
        val viewModel = hub(*everyBoard()).viewModel()

        val all = viewModel.overall(Games.All)
        assertEquals(listOf("u1" to 250.0, "maya" to 200.0, "jonas" to 100.0), totals(all.board))
        assertTrue(RanksBoard.Game(strands) in all.places.map { it.board })
        assertEquals(5, all.boardCount)

        val linkedIn = viewModel.overall(Games.LinkedIn)
        assertEquals(listOf("maya" to 150.0, "u1" to 150.0, "jonas" to 100.0), totals(linkedIn.board))
        assertEquals(listOf(1, 1, 3), rowsOf(linkedIn.board).map { it.rank })
        assertEquals(listOf(RanksBoard.Overall, RanksBoard.Game(ZIP), RanksBoard.Game(TANGO), RanksBoard.Game(QUEENS)), linkedIn.places.map { it.board })
        assertEquals(4, linkedIn.boardCount)
        assertEquals(4, linkedIn.boards.size)
    }

    @Test
    fun `LinkedIn games in Friends sums the viewer and their friends over the LinkedIn games only`() = runTest {
        val h = hub(*everyBoard())
        h.befriend("maya")
        val viewModel = h.viewModel()

        val all = viewModel.overall(Games.All, RankScope.Friends)
        assertEquals(listOf("u1" to 250.0, "maya" to 200.0), totals(all.board))

        val linkedIn = viewModel.overall(Games.LinkedIn, RankScope.Friends)
        assertEquals(listOf("maya" to 150.0, "u1" to 150.0), totals(linkedIn.board))
        assertFalse(RanksBoard.Game(strands) in linkedIn.places.map { it.board })
    }

    @Test
    fun `LinkedIn games counts the Week and the Month over the LinkedIn games only, with week and month points on or off`() = runTest {
        listOf(FakeReleaseFlags(), FakeReleaseFlags(HistoryFlag.WeekMonthPoints)).forEach { flags ->
            val viewModel = hub(*everyBoard()).viewModel(releaseFlags = flags)

            listOf(Window.Week, Window.Month).forEach { window ->
                val all = viewModel.overall(Games.All, window = window)
                val linkedIn = viewModel.overall(Games.LinkedIn, window = window)

                assertEquals("$window with week and month points ${HistoryFlag.WeekMonthPoints in flags.enabled.value}", 250.0, totals(all.board).single { it.first == "u1" }.second, 0.0)
                assertEquals("$window", 150.0, totals(linkedIn.board).single { it.first == "u1" }.second, 0.0)
                assertFalse(RanksBoard.Game(strands) in linkedIn.places.map { it.board })
            }
        }
    }

    @Test
    fun `x of y counts only the LinkedIn games under LinkedIn games`() = runTest {
        val viewModel = hub(zip, strandsBoard).viewModel()

        assertEquals("2 of 4 games", viewModel.overall(Games.All).board.note)
        assertEquals("1 of 3 games", viewModel.overall(Games.LinkedIn).board.note)
    }

    @Test
    fun `games played counts only the LinkedIn games under LinkedIn games`() = runTest {
        val viewModel = hub(*everyBoard()).viewModel()

        val me = { slice: RanksBoardSlice -> rowsOf(slice).single { it.playerId == "u1" }.scoreValue as BoardRowValue.Points }
        assertEquals(3, me(viewModel.overall(Games.All).board).games)
        assertEquals(2, me(viewModel.overall(Games.LinkedIn).board).games)
    }

    @Test
    fun `switching back to All games restores the full Overall and the full list`() = runTest {
        val viewModel = hub(*everyBoard()).viewModel()
        val before = viewModel.overall(Games.All)

        viewModel.overall(Games.LinkedIn)
        val after = viewModel.overall(Games.All)

        assertEquals(totals(before.board), totals(after.board))
        assertEquals(before.places.map { it.board }, after.places.map { it.board })
        assertEquals(5, after.boardCount)
    }

    @Test
    fun `picking LinkedIn games on Strands opens Overall, and on a LinkedIn game keeps that game`() = runTest {
        val viewModel = hub(*everyBoard()).viewModel()

        viewModel.pickBoard(RanksBoard.Game(strands))
        viewModel.setGames(Games.LinkedIn)
        assertEquals(RanksBoard.Overall, viewModel.selection.value.board)

        viewModel.pickBoard(RanksBoard.Game(QUEENS))
        viewModel.setGames(Games.All)
        viewModel.setGames(Games.LinkedIn)
        assertEquals(RanksBoard.Game(QUEENS), viewModel.selection.value.board)
    }

    @Test
    fun `each LinkedIn game's own board is the same under both picks`() = runTest {
        val viewModel = hub(*everyBoard()).viewModel()

        listOf(ZIP, TANGO, QUEENS).forEach { game ->
            viewModel.pickBoard(RanksBoard.Game(game))
            val all = viewModel.overall(Games.All).board
            val linkedIn = viewModel.overall(Games.LinkedIn).board
            assertEquals(game.value, all, linkedIn)
        }
    }

    @Test
    fun `a swipe from the last LinkedIn game does not reach Strands, and does under All games`() = runTest {
        val viewModel = hub(*everyBoard()).viewModel()
        viewModel.pickBoard(RanksBoard.Game(QUEENS))

        viewModel.setGames(Games.LinkedIn)
        viewModel.swipe(1)
        assertEquals(RanksBoard.Game(QUEENS), viewModel.selection.value.board)

        viewModel.setGames(Games.All)
        viewModel.swipe(1)
        assertEquals(RanksBoard.Game(strands), viewModel.selection.value.board)
    }

    @Test
    fun `the position is the board's place among the listed ones`() = runTest {
        val viewModel = hub(*everyBoard()).viewModel()
        viewModel.pickBoard(RanksBoard.Game(QUEENS))

        val state = viewModel.overall(Games.LinkedIn)

        assertEquals(3 to 4, state.position to state.boardCount)
    }

    @Test
    fun `a saved LinkedIn games default opens the hub on it, and a link to Strands keeps All games`() = runTest {
        val h = hub(*everyBoard(), linkedInDefault = true)

        assertEquals(Games.LinkedIn, h.viewModel().selection.value.games)
        assertEquals(Games.All, h.viewModel(opening = RanksSelection.opening(RanksBoard.Game(strands), null)).selection.value.games)
        assertEquals(Games.LinkedIn, h.viewModel(opening = RanksSelection.opening(RanksBoard.Game(ZIP), null)).selection.value.games)
    }

    @Test
    fun `without a saved default the hub opens on All games, and a pick not saved is gone next time`() = runTest {
        val h = hub(*everyBoard())
        h.viewModel().setGames(Games.LinkedIn)

        assertEquals(Games.All, h.viewModel().selection.value.games)
    }

    @Test
    fun `a reselect reset lands on the saved games default`() = runTest {
        val viewModel = hub(*everyBoard(), linkedInDefault = true).viewModel()
        viewModel.setGames(Games.All)

        viewModel.resetSelection()

        assertEquals(Games.LinkedIn, viewModel.selection.value.games)
    }
}
