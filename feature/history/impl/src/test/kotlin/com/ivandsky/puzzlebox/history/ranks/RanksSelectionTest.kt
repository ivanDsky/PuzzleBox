package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every pure transition of the hub's selection, and where a fresh or reset one lands (A4). */
class RanksSelectionTest {

    private val landing = RanksSelection()

    @Test
    fun `a fresh selection is Overall, today, Global, calendar folded`() {
        assertEquals(RanksBoard.Overall, landing.board)
        assertEquals(RanksSelection.Window.Day, landing.window)
        assertEquals(RankScope.Global, landing.scope)
        assertEquals(null, landing.day)
        assertEquals(false, landing.calendarExpanded)
    }

    @Test
    fun `pickBoard changes only the board`() {
        val picked = landing.pickBoard(RanksBoard.Game(TANGO))

        assertEquals(landing.copy(board = RanksBoard.Game(TANGO)), picked)
    }

    @Test
    fun `pickDay changes only the day, and null is today again`() {
        val day = LocalDate.of(2026, 11, 6)

        assertEquals(landing.copy(day = day), landing.pickDay(day))
        assertEquals(landing, landing.pickDay(day).pickDay(null))
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
    fun `toggleCalendar flips the calendar and back`() {
        val open = landing.toggleCalendar()

        assertEquals(landing.copy(calendarExpanded = true), open)
        assertEquals(landing, open.toggleCalendar())
    }

    @Test
    fun `transitions return new values and leave the receiver untouched`() {
        val before = landing.pickBoard(RanksBoard.Game(QUEENS))

        before.pickDay(LocalDate.of(2026, 11, 6)).setScope(RankScope.Friends).toggleCalendar()

        assertEquals(RanksSelection(board = RanksBoard.Game(QUEENS)), before)
    }

    @Test
    fun `reset returns to Overall, today, Global from any selection`() {
        val busy = landing
            .pickBoard(RanksBoard.Game(ZIP))
            .pickDay(LocalDate.of(2026, 11, 6))
            .setWindow(RanksSelection.Window.Month)
            .setScope(RankScope.Friends)
            .toggleCalendar()

        assertEquals(landing, busy.reset())
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
