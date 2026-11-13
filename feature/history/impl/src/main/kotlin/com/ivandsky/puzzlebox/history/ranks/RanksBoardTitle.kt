package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/** A picked day names its day, week (by its Monday) or month; no pick is the live "today" / "this week" / "this month". */
internal fun boardTitle(name: String, kind: RanksSelection.Window, day: LocalDate?) = when {
    day == null -> when (kind) {
        RanksSelection.Window.Day -> dailyBoardTitle(name)
        RanksSelection.Window.Week -> "$name · this week"
        RanksSelection.Window.Month -> "$name · this month"
    }
    kind == RanksSelection.Window.Day -> "$name · ${day.format(DAY_LABEL)}"
    kind == RanksSelection.Window.Week -> "$name · week of ${day.with(DayOfWeek.MONDAY).format(DAY_LABEL)}"
    else -> "$name · ${day.format(MONTH_LABEL)}"
}
