package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * One game-agnostic history row (fix round 5, defect C): the daily identity (ADR-0004's
 * normalized `gameId + day + puzzleNumber` — the [PuzzleRef] carries it, no game-specific
 * casting) plus the day's record when the puzzle has been solved. This is the projection
 * every history surface (Home teaser, History browser) consumes: a Queens completion
 * surfaces exactly like a Zip completion, and captured-but-unfinished Zip days keep
 * their existing rows (the Zip compete loop's captured-day surface, unchanged).
 */
data class GameHistoryDay(
    val gameId: GameId,
    val day: LocalDate,
    val puzzleNumber: Int?,
    /** The day's record row when solved — null for a captured-but-unfinished day. */
    val record: CompletionRecord?,
    /** Capture recency when the day was fetched into a compete-loop cache (Zip/Tango);
     *  0 for rows that exist only as a completion record. */
    val fetchedAtMs: Long = 0L,
)

/**
 * The merged, newest-first history across games: every record row (any game — the
 * completions store is game-agnostic) UNION the Zip captured days (replayable through
 * the Zip compete loop's cache; a solved Zip day merges into its captured row instead
 * of duplicating it). Rows whose key no registered game can decode are dropped upstream
 * by the repository's preserve-or-drop read (A19), so every record here is decodable.
 *
 * Ordering is stable: records arrive newest-first (firstCompletedAtMs DESC) and Zip
 * entries newest-first, so same-day rows across games keep that order after the
 * day-descending sort.
 */
fun gameHistoryDays(
    zip: ZipDailyRepository,
    completions: CompletionRepository,
): Flow<List<GameHistoryDay>> = combine(
    zip.observeHistory(),
    completions.observeRecords(),
) { entries, records ->
    val rows = LinkedHashMap<Pair<GameId, LocalDate>, GameHistoryDay>()

    // Every game's solved day — typed identity straight off the ref, no casting.
    for (record in records) {
        val ref = record.puzzleRef
        rows[ref.gameId to ref.date] =
            GameHistoryDay(ref.gameId, ref.date, ref.puzzleNumber, record)
    }

    // The Zip compete loop's captured days keep their richer identity (edition number,
    // replayable cache); a solved Zip day merges into its captured row.
    for (entry in entries) {
        val key = ZipDefinition.id to entry.day
        val solved = rows[key]
        rows[key] = GameHistoryDay(
            gameId = ZipDefinition.id,
            day = entry.day,
            puzzleNumber = entry.puzzleNumber ?: solved?.puzzleNumber,
            record = solved?.record,
            fetchedAtMs = entry.fetchedAtMs,
        )
    }

    rows.values.sortedByDescending { it.day }
}

/**
 * The per-game display line for a history row, resolved against the app's aggregated
 * plugin registry (ADR-0001) — the same aggregation Home's featured game uses. Games
 * removed from the build fall back to their raw id rather than vanishing from history.
 */
fun gameDisplayName(gameId: GameId, registered: List<Pair<GameId, String>>): String =
    registered.firstOrNull { it.first == gameId }?.second ?: gameId.value
