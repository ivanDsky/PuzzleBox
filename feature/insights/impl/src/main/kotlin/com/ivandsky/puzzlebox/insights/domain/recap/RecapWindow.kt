package com.ivandsky.puzzlebox.insights.domain.recap

import java.time.LocalDate
import java.time.Month

/** When Today offers a recap, by the puzzle day: the single source for the due check and for Home's skeleton. */
object RecapWindow {

    /** The month Today card's window: days 1–3 of the month after the one it recaps. */
    private const val MONTH_DUE_DAYS = 3

    /** The year Today card's window: Jan 1–7 for the year just ended. */
    private const val YEAR_DUE_DAYS = 7

    /** Whether the month just ended is offered on [today]. */
    fun offersMonth(today: LocalDate): Boolean = today.dayOfMonth <= MONTH_DUE_DAYS

    /** Whether the year just ended is offered on [today]. */
    fun offersYear(today: LocalDate): Boolean = today.month == Month.JANUARY && today.dayOfMonth <= YEAR_DUE_DAYS

    /** Whether any recap can be due on [today]. */
    fun isOpen(today: LocalDate): Boolean = offersMonth(today) || offersYear(today)
}
