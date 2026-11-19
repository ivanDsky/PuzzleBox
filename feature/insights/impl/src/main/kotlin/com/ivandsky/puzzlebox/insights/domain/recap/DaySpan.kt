package com.ivandsky.puzzlebox.insights.domain.recap

import java.time.LocalDate

/**
 * A stretch of puzzle days the one fold ([ComputePeriodFacts]) cuts its facts to: a recap's calendar [RecapPeriod], or
 * a range of days the stats page asks for. A day belongs to a span by its puzzle day, never by the instant its result
 * was recorded or imported.
 */
internal interface DaySpan {

    /** The span's first puzzle day. */
    val first: LocalDate

    /** Whether the puzzle [day] falls inside the span. */
    operator fun contains(day: LocalDate): Boolean
}
