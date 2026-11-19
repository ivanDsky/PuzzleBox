package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every pure transition of the hub's selection, and where a fresh or reset one lands (A4). */
class RanksSelectionTest {

    private val landing = RanksSelection()

    /** The boards LinkedIn games lists when Queens is not one of the LinkedIn games. */
    private val linkedInBoards = listOf(RanksBoard.Overall, RanksBoard.Game(ZIP), RanksBoard.Game(TANGO))

    @Test
    fun `a fresh selection is Overall, today, Global, calendar folded`() {
        assertEquals(RanksBoard.Overall, landing.board)
        assertEquals(RanksSelection.Window.Day, landing.window)
        assertEquals(RankScope.Global, landing.scope)
        assertEquals(null, landing.day)
        assertEquals(false, landing.calendarExpanded)
        assertEquals(RanksSelection.Games.All, landing.games)
    }

    @Test
    fun `pickBoard changes only the board`() {
        val picked = landing.pickBoard(RanksBoard.Game(TANGO))

        assertEquals(landing.copy(board = RanksBoard.Game(TANGO)), picked)
    }

    @Test
    fun `step walks the boards in order and stays put at either end`() {
        val boards = listOf(RanksBoard.Overall, RanksBoard.Game(ZIP), RanksBoard.Game(TANGO))

        assertEquals(RanksBoard.Game(ZIP), landing.step(1, boards).board)
        assertEquals(RanksBoard.Game(TANGO), landing.pickBoard(RanksBoard.Game(ZIP)).step(1, boards).board)
        assertEquals(RanksBoard.Overall, landing.pickBoard(RanksBoard.Game(ZIP)).step(-1, boards).board)
        assertEquals(landing, landing.step(-1, boards))
        val last = landing.pickBoard(RanksBoard.Game(TANGO))
        assertEquals(last, last.step(1, boards))
    }

    @Test
    fun `step keeps everything but the board, and ignores a board that is not listed`() {
        val boards = listOf(RanksBoard.Overall, RanksBoard.Game(ZIP))
        val picked = landing.setWindow(RanksSelection.Window.Week).copy(calendarExpanded = true)

        assertEquals(picked.copy(board = RanksBoard.Game(ZIP)), picked.step(1, boards))
        val unlisted = landing.pickBoard(RanksBoard.Game(QUEENS))
        assertEquals(unlisted, unlisted.step(1, boards))
    }

    @Test
    fun `setWindow changes only the window`() {
        assertEquals(landing.copy(window = RanksSelection.Window.Week), landing.setWindow(RanksSelection.Window.Week))
        assertEquals(landing.copy(window = RanksSelection.Window.Month), landing.setWindow(RanksSelection.Window.Month))
    }

    @Test
    fun `setScope changes only the scope`() {
        assertEquals(landing.copy(scope = RankScope.Friends), landing.setScope(RankScope.Friends))
    }

    @Test
    fun `transitions return new values and leave the receiver untouched`() {
        val before = landing.pickBoard(RanksBoard.Game(QUEENS))

        before.pickFromCalendar(LocalDate.of(2026, 11, 6)).setScope(RankScope.Friends).showCalendar(LocalDate.of(2026, 11, 6), expanded = true)

        assertEquals(RanksSelection(board = RanksBoard.Game(QUEENS)), before)
    }

    @Test
    fun `reset returns to Overall, today, Global from any selection`() {
        val busy = landing
            .pickBoard(RanksBoard.Game(ZIP))
            .pickFromCalendar(LocalDate.of(2026, 11, 6))
            .setWindow(RanksSelection.Window.Month)
            .setScope(RankScope.Friends)
            .showCalendar(LocalDate.of(2026, 11, 6), expanded = true)

        assertEquals(landing, busy.reset(RankScope.Global, RanksSelection.Games.All))
        assertEquals(landing.setScope(RankScope.Friends), busy.reset(RankScope.Friends, RanksSelection.Games.All))
    }

    @Test
    fun `reset lands on the player's default games`() {
        val busy = landing.pickBoard(RanksBoard.Game(ZIP)).setGames(RanksSelection.Games.LinkedIn, linkedInBoards)

        assertEquals(landing.copy(games = RanksSelection.Games.LinkedIn), busy.reset(RankScope.Global, RanksSelection.Games.LinkedIn))
    }

    @Test
    fun `setGames keeps a board the picked games still list`() {
        val onZip = landing.pickBoard(RanksBoard.Game(ZIP))

        assertEquals(onZip.copy(games = RanksSelection.Games.LinkedIn), onZip.setGames(RanksSelection.Games.LinkedIn, linkedInBoards))
    }

    @Test
    fun `setGames moves a board the picked games hide to Overall`() {
        val onQueens = landing.pickBoard(RanksBoard.Game(QUEENS))

        assertEquals(landing.copy(games = RanksSelection.Games.LinkedIn), onQueens.setGames(RanksSelection.Games.LinkedIn, linkedInBoards))
    }

    @Test
    fun `land sets the default games only when the open board is still listed`() {
        assertEquals(landing.copy(games = RanksSelection.Games.LinkedIn), landing.land(RanksSelection.Games.LinkedIn, linkedInBoards))

        val linkToQueens = landing.pickBoard(RanksBoard.Game(QUEENS))
        assertEquals(linkToQueens, linkToQueens.land(RanksSelection.Games.LinkedIn, linkedInBoards))
    }

    @Test
    fun `rankBoards is Overall then every game in registration order`() {
        assertEquals(
            listOf(RanksBoard.Overall, RanksBoard.Game(ZIP), RanksBoard.Game(TANGO), RanksBoard.Game(QUEENS)),
            rankBoards(HistoryTestSupport.registry),
        )
    }

    @Test
    fun `a window kind is the domain window at its anchor`() {
        val anchor = LocalDate.of(2026, 11, 6)

        assertEquals(RankWindow.Day(anchor), RanksSelection.Window.Day.at(anchor))
        assertEquals(RankWindow.Week(anchor), RanksSelection.Window.Week.at(anchor))
        assertEquals(RankWindow.Month(anchor), RanksSelection.Window.Month.at(anchor))
    }
}
