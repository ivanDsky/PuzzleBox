package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One rendered history row: a captured day and its identity (A8). */
data class HistoryDayRow(
    val day: LocalDate,
    val puzzleNumber: Int?,
) {
    /** "Saturday, November 1" — the same date voice home's teaser uses. */
    val headline: String = day.format(DAY_FORMAT)

    val label: String
        get() = if (puzzleNumber != null) "Daily Zip · Puzzle #$puzzleNumber" else "Daily Zip"

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    }
}

internal fun ZipDailyEntry.toRow() = HistoryDayRow(day = day, puzzleNumber = puzzleNumber)

/** Score contract (A3): time + hints only. "1:35", hours only when they exist. */
internal fun formatScore(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** "Captured Oct 12, 2026" — when the app first fetched and cached this day. */
internal fun formatCapturedAt(fetchedAtMs: Long): String =
    Instant.ofEpochMilli(fetchedAtMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(CAPTURED_FORMAT)

private val CAPTURED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
