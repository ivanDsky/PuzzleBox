package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
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
 * One rendered history row: a captured day and its identity (A8), per game (ADR-0004;
 * tango-05: Zip and Tango share the calendar — every consumer keys by game+day). Rows
 * render their game's display name instead of assuming Zip (fix round 5, defect C).
 * [fetchedAtMs] is 0 for rows that came only from a completion record (never captured
 * as a replayable day); it only orders rows within a day.
 */
data class HistoryDayRow(
    val day: LocalDate,
    val puzzleNumber: Int?,
    val gameId: GameId,
    val gameName: String,
    val fetchedAtMs: Long = 0L,
) {
    /** "Saturday, November 1" — the same date voice home's teaser uses. */
    val headline: String = day.format(DAY_FORMAT)

    /** A day with no edition number is practice (the Tango seed), for every game. */
    val label: String
        get() = if (puzzleNumber != null) "Daily $gameName · Puzzle #$puzzleNumber" else "Practice $gameName"

    /** List/detail identity: `zip/2026-10-31` — stable across recomposition and pages. */
    val key: String get() = "${gameId.value}/$day"

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
    fetchedAtMs = fetchedAtMs,
)
