package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One rendered history row: a captured or solved day, per game (ADR-0004: Zip and Tango
 * share the calendar, so every consumer keys by game+day). [fetchedAtMs] is 0 for rows
 * that came only from a completion record (never captured as a replayable day); it only
 * orders rows within a day. [record] is that day's solve (null = not finished yet);
 * [isBest] marks the game's one fastest numbered solve, the same rule as
 * `GameStats.bestTimeMs` (practice never counts).
 */
data class HistoryDayRow(
    val day: LocalDate,
    val puzzleNumber: Int?,
    val gameId: GameId,
    val gameName: String,
    val fetchedAtMs: Long = 0L,
    val brand: GameBrand = GameBrand.Neutral,
    val record: CompletionRecord? = null,
    val isBest: Boolean = false,
    /** Opened after its own day (ADR-0012): tagged "From the archive", off the streak. */
    val archived: Boolean = false,
    /** The year "today" falls in; days from any other year spell theirs out (A23). */
    val currentYear: Int = LocalDate.now().year,
) {
    /** "Saturday, November 1" — the detail's date line; an archive day from another year
     *  reads "Monday, March 4, 2024" so it can't pass for this year's. */
    val headline: String = day.format(if (day.year == currentYear) DAY_FORMAT else DAY_YEAR_FORMAT)

    /** "#592", or "Practice" for a day with no edition number (the Tango seed). */
    val edition: String
        get() = puzzleNumber?.let { "#$it" } ?: "Practice"

    /** "Zip · #592" / "Tango · Practice" — the detail's title, as the play top bar and Result name it. */
    val title: String
        get() = "$gameName · $edition"

    /** A numbered day with a solve: the only kind that counts for streaks and best times. */
    val counts: Boolean
        get() = record != null && puzzleNumber != null

    /** "Opened Nov 5 · doesn't count toward your streak" for an archived day (A21); the
     *  open date is the capture's, on the device's calendar. Null for a day played live. */
    val archiveNote: String?
        get() = if (!archived) {
            null
        } else {
            val opened = Instant.ofEpochMilli(fetchedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val date = opened.format(if (opened.year == currentYear) OPENED_FORMAT else OPENED_YEAR_FORMAT)
            "Opened $date · doesn't count toward your streak"
        }

    /** List/detail identity: `zip/2026-10-31` — stable across recomposition and pages. */
    val key: String get() = "${gameId.value}/$day"

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        val DAY_YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
        val OPENED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
        val OPENED_YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    }
}

/** The row projection of one merged game-agnostic history day. */
internal fun GameHistoryDay.toRow(gameName: String, brand: GameBrand, currentYear: Int) = HistoryDayRow(
    day = day,
    puzzleNumber = puzzleNumber,
    gameId = gameId,
    gameName = gameName,
    fetchedAtMs = fetchedAtMs,
    brand = brand,
    record = record,
    archived = archived,
    currentYear = currentYear,
)

/**
 * Marks each game's best row: its fastest numbered solve, the earliest day on a tie —
 * the one row `ObserveGameStats` reads `bestTimeMs` from.
 */
internal fun List<HistoryDayRow>.markBest(): List<HistoryDayRow> {
    val best = mapNotNull { row -> row.record?.takeIf { row.puzzleNumber != null }?.let { row to it.time } }
        .groupBy { (row, _) -> row.gameId }
        .mapNotNull { (_, solves) -> solves.minWithOrNull(compareBy({ it.second }, { it.first.day }))?.first?.key }
        .toSet()
    return map { if (it.key in best) it.copy(isBest = true) else it }
}

/**
 * The brands of [rows]' numbered solves per day, in [order] — practice never fills a
 * dot, the same rule as the streak.
 */
internal fun solvedByDay(rows: List<HistoryDayRow>, order: List<GameId>): Map<LocalDate, List<GameBrand>> {
    val rank = order.withIndex().associate { (index, id) -> id to index }
    return rows
        .filter { it.counts }
        .sortedBy { rank[it.gameId] ?: Int.MAX_VALUE }
        .groupBy({ it.day }, { it.brand })
}
