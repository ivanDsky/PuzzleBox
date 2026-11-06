package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * One game-agnostic history row (fix round 5, defect C): the daily identity (ADR-0004's
 * normalized `gameId + day + puzzleNumber` — the [com.ivandsky.puzzlebox.game.PuzzleRef]
 * carries it, no game-specific casting) plus the day's record when the puzzle has been
 * solved. This is the projection every history surface (Home teaser, History browser)
 * consumes: every game's completion and captured-but-unfinished day surface the same way.
 */
data class GameHistoryDay(
    val gameId: GameId,
    val day: LocalDate,
    val puzzleNumber: Int?,
    /** The day's record row when solved — null for a captured-but-unfinished day. */
    val record: CompletionRecord?,
    /** Capture recency when the day was fetched into the daily cache; 0 for rows that
     *  exist only as a completion record. */
    val fetchedAtMs: Long = 0L,
    /** Played from the archive (A23) rather than captured as that day's daily; false for
     *  rows that exist only as a completion record. */
    val archived: Boolean = false,
)

/**
 * The merged, newest-first history across games: every listed game's record rows
 * UNION the captured days of every game in [GameRegistry.all] (replayable through the daily cache; a solved day merges into its captured
 * row instead of duplicating it). Rows whose key no registered game can decode are
 * dropped upstream by the repository's preserve-or-drop read (A19), so every record here
 * is decodable. A custom (friend-puzzle) record has no day and adds no row (A12). An
 * unlisted game's records (the debug 9x9, ADR-0015) are dropped here, so
 * no history, Home, stats or Profile surface ever shows them.
 *
 * Ordering is stable: records arrive newest-first (firstCompletedAtMs DESC) and each
 * game's entries newest-first (games in registration order), so same-day rows across
 * games keep that order after the day-descending sort.
 */
class ObserveGameHistory(
    private val registry: GameRegistry,
    private val daily: DailyRepository,
    private val completions: CompletionRepository,
) {

    operator fun invoke(): Flow<List<GameHistoryDay>> {
        val games = registry.all.map { it.definition.id }
        val listed = games.toSet()
        val captured: Flow<List<DailyEntry>> =
            if (games.isEmpty()) flowOf(emptyList())
            else combine(games.map { daily.observeHistory(it) }) { perGame -> perGame.flatMap { it } }
        return combine(captured, completions.observeRecords()) { entries, records ->
            val rows = LinkedHashMap<Pair<GameId, LocalDate>, GameHistoryDay>()

            // Every game's solved day — typed identity straight off the ref, no casting.
            for (record in records) {
                val ref = record.puzzleRef
                if (ref.gameId !in listed || ref.key !is PuzzleKey.Daily) continue
                rows[ref.gameId to ref.date] =
                    GameHistoryDay(ref.gameId, ref.date, ref.puzzleNumber, record)
            }

            // Captured days keep their richer identity (edition number, replayable cache);
            // a solved day merges into its captured row.
            for (entry in entries) {
                val key = entry.gameId to entry.day
                val solved = rows[key]
                rows[key] = GameHistoryDay(
                    gameId = entry.gameId,
                    day = entry.day,
                    puzzleNumber = entry.puzzleNumber ?: solved?.puzzleNumber,
                    record = solved?.record,
                    fetchedAtMs = entry.fetchedAtMs,
                    archived = entry.archived,
                )
            }

            rows.values.sortedByDescending { it.day }
        }
    }
}
