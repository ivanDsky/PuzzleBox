package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardRead
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The line under a Friends day board that says when its LinkedIn part was last read (backlog #1357), and the minute that
 * keeps it current: [minutes] emits at once and then once a minute, for as long as it is collected.
 */
internal class RanksFreshness(
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    val minutes: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_MS)
        }
    }

    /** [read] as the line says it, by the clock's now. */
    fun line(read: LinkedInBoardRead): String = freshnessLine(read, clock.instant(), zone, locale)

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}

/**
 * How long ago [read] was, in the words the player reads under the board: "just now" under a minute, "N min ago" under an
 * hour, the clock time for the rest of that day and the date and time after it. A board read after its day ended is the day's
 * last, so it says "final" and when, whatever its age: it never needs another read.
 */
internal fun freshnessLine(read: LinkedInBoardRead, now: Instant, zone: ZoneId, locale: Locale): String {
    val readAt = Instant.ofEpochMilli(read.atMs).atZone(zone)
    val time = readAt.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    val dated = readAt.format(DateTimeFormatter.ofPattern("MMM d, HH:mm", locale))
    if (read.complete) return "LinkedIn final · read $dated"
    // A read stamped after now (the phone's clock went back) is as fresh as it gets.
    val minutes = Duration.between(readAt.toInstant(), now).toMinutes()
    return when {
        minutes < 1 -> "LinkedIn updated just now"
        minutes < 60 -> "LinkedIn updated $minutes min ago"
        readAt.toLocalDate() == now.atZone(zone).toLocalDate() -> "LinkedIn updated at $time"
        else -> "LinkedIn updated $dated"
    }
}
