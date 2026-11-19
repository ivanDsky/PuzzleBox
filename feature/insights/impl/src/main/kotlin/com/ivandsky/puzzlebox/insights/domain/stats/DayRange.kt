package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.insights.domain.recap.DaySpan
import java.time.LocalDate

/** The puzzle days from [first] to [last], both included: the span a Week or All range cuts the facts to. */
internal data class DayRange(override val first: LocalDate, val last: LocalDate) : DaySpan {

    override fun contains(day: LocalDate): Boolean = day >= first && day <= last
}
