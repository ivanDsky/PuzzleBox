package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.data.repository.GameHistoryDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One rendered history row's identity: game + day (fix round 5, defect C — rows are
 *  per-game; two games can both own a day). */
data class RowKey(val gameId: GameId, val day: LocalDate) : Comparable<RowKey> {
    /** Process-death-saveable string form. */
    val saveKey: String = "${gameId.value}|$day"

    override fun compareTo(other: RowKey): Int =
        compareValuesBy(this, other, { it.day }, { it.gameId.value })

    companion object {
        fun fromSaveKey(key: String): RowKey? = key.split('|').let { parts ->
            parts.getOrNull(0)?.let { gameId ->
                parts.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.let { day -> RowKey(GameId(gameId), day) }
            }
        }
    }
}

/**
 * One rendered history row: a captured day and its identity (A8), per game (ADR-0004) —
 * rows render their game's display name instead of assuming Zip (fix round 5, defect C).
 */
data class HistoryDayRow(
    val day: LocalDate,
    val puzzleNumber: Int?,
    val gameId: GameId,
    val gameName: String,
) {
    /** "Saturday, November 1" — the same date voice home's teaser uses. */
    val headline: String = day.format(DAY_FORMAT)

    val label: String
        get() = if (puzzleNumber != null) "Daily $gameName · Puzzle #$puzzleNumber" else "Daily $gameName"

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    }
}

/** The row projection of one merged game-agnostic history day. */
internal fun GameHistoryDay.toRow(gameName: String) = HistoryDayRow(
    day = day,
    puzzleNumber = puzzleNumber,
    gameId = gameId,
    gameName = gameName,
)

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
