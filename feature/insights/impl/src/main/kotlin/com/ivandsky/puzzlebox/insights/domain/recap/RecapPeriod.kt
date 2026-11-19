package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import java.time.LocalDate
import java.time.YearMonth

/**
 * The calendar span a recap covers: one month or one year of puzzle days (A2, A3). A day belongs to a period by its
 * puzzle day, never by the instant its result was recorded or imported.
 */
internal sealed interface RecapPeriod : DaySpan {
    /** `YYYY-MM` for a month, `YYYY` for a year: the id the recap's navigation key carries and [parse] reads back. */
    val id: String

    /** The Ranks window a friends read of this period uses; null for a year, which reads the kept weekly history (A12). */
    val rankWindow: RankWindow?

    data class Month(val month: YearMonth) : RecapPeriod {
        override val id: String get() = month.toString()
        override val first: LocalDate get() = month.atDay(1)
        override val rankWindow: RankWindow get() = RankWindow.Month(month.atDay(1))
        override fun contains(day: LocalDate): Boolean = YearMonth.from(day) == month
    }

    data class Year(val year: Int) : RecapPeriod {
        override val id: String get() = year.toString().padStart(4, '0')
        override val first: LocalDate get() = LocalDate.of(year, 1, 1)
        override val rankWindow: RankWindow? get() = null
        override fun contains(day: LocalDate): Boolean = day.year == year
    }

    companion object {
        private val yearId = Regex("""\d{4}""")
        private val monthId = Regex("""\d{4}-(0[1-9]|1[0-2])""")

        /** The period named by [id], or null for an id this build does not read (a stale or hand-edited key). */
        fun parse(id: String): RecapPeriod? = when {
            monthId.matches(id) -> Month(YearMonth.parse(id))
            yearId.matches(id) -> Year(id.toInt())
            else -> null
        }
    }
}
