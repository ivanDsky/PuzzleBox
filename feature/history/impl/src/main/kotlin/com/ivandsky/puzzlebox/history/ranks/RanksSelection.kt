package com.ivandsky.puzzlebox.history.ranks

import java.time.LocalDate

/**
 * What the player picked in the hub: the [board], the [window] and [scope] it ranks over, the
 * [day] (null = each game's calendar today, so the midnight rollover needs no write here) and
 * whether the calendar is open to the month. The view model holds one of these; every control
 * changes it through the transitions below and every other state derives from it.
 */
internal data class RanksSelection(
    val board: RanksBoard = RanksBoard.Overall,
    val window: Window = Window.Day,
    val scope: Scope = Scope.Global,
    val day: LocalDate? = null,
    val calendarExpanded: Boolean = false,
) {
    enum class Window { Day, Week, Month }

    enum class Scope { Global, Friends }

    fun pickBoard(board: RanksBoard) = copy(board = board)

    fun pickDay(day: LocalDate?) = copy(day = day)

    fun setWindow(window: Window) = copy(window = window)

    fun setScope(scope: Scope) = copy(scope = scope)

    fun toggleCalendar() = copy(calendarExpanded = !calendarExpanded)

    /** Back to the landing choice: Overall, today, Global (A4). The calendar folds too. */
    fun reset() = RanksSelection()
}
