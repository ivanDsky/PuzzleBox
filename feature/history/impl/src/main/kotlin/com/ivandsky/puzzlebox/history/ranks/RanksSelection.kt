package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import java.time.LocalDate

/**
 * What the player picked in the hub: the [board], the [window] and [scope] it ranks over, the
 * [day] (null = each game's calendar today, so the midnight rollover needs no write here) and
 * whether the calendar is open to the month, at the week or month holding [shownDay] (null = the picked day's, else today's). The view model holds one of these; every control
 * changes it through the transitions below and every other state derives from it.
 */
internal data class RanksSelection(
    val board: RanksBoard = RanksBoard.Overall,
    val window: Window = Window.Day,
    val scope: RankScope = RankScope.Global,
    val day: LocalDate? = null,
    val calendarExpanded: Boolean = false,
    val shownDay: LocalDate? = null,
) {
    /** The span the player chose; the domain's [RankWindow] is that span at an anchor day, which [day] and the calendar supply. */
    enum class Window {
        Day,
        Week,
        Month,
        ;

        fun at(anchor: LocalDate): RankWindow = when (this) {
            Day -> RankWindow.Day(anchor)
            Week -> RankWindow.Week(anchor)
            Month -> RankWindow.Month(anchor)
        }
    }

    fun pickBoard(board: RanksBoard) = copy(board = board)

    /** A swipe: [delta] boards along [boards]; stays put at either end (no wrap) or when the board is not listed. */
    fun step(delta: Int, boards: List<RanksBoard>): RanksSelection {
        val at = boards.indexOf(board)
        val target = if (at < 0) null else boards.getOrNull(at + delta)
        return if (target == null) this else pickBoard(target)
    }

    fun pickDay(day: LocalDate?) = copy(day = day)

    /** A day tapped on the calendar: it is the board's day, and the calendar folds back to the week holding it. */
    fun pickFromCalendar(day: LocalDate?) = copy(day = day, shownDay = day, calendarExpanded = false)

    /** Pages the calendar to the week (or, [expanded], month) holding [day]; the picked day stays. */
    fun showCalendar(day: LocalDate, expanded: Boolean) = copy(shownDay = day, calendarExpanded = expanded)

    fun setWindow(window: Window) = copy(window = window)

    fun setScope(scope: RankScope) = copy(scope = scope)

    fun toggleCalendar() = copy(calendarExpanded = !calendarExpanded)

    /** Back to the landing choice: Overall, today, Global (A4). The calendar folds too. */
    fun reset() = RanksSelection()
}
