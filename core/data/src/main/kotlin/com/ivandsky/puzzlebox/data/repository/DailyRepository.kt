package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * UI-state contract for "today's puzzle" of one game (A7/A8). The fetch outcome and the
 * cache are combined so the UI never has to: [Fresh] is a new official daily; [Cached] is
 * today's previously captured puzzle plus the sealed fetch error (banner state, board
 * still playable); [Unavailable] is nothing captured yet.
 */
sealed interface DailyToday {
    data class Fresh(val puzzle: Puzzle) : DailyToday
    data class Cached(val puzzle: Puzzle, val error: FetchError) : DailyToday
    data class Unavailable(val error: FetchError) : DailyToday
}

/**
 * One captured day of one game (History rows, streak/analytics teaser data). [archived]
 * marks a row fetched on a later day than its own (in the game's zone, ADR-0012) — an
 * archive pick rather than a day played live.
 */
data class DailyEntry(
    val gameId: GameId,
    val day: LocalDate,
    val puzzleNumber: Int?,
    val variant: String,
    val fetchedAtMs: Long,
    val archived: Boolean = false,
)

/**
 * The daily compete loop's data for every registered game, keyed by [GameId]. Every
 * fetched day is cached in Room. Today is fetched with cache degrade (A7); a past day is
 * read by date (ADR-0012): cache first, and fetched on a miss only when the game's
 * source declares an archive (`DailySource.archiveSince`); without one, past days stay
 * cache-only (A8).
 *
 * The player's record (first in-app completion, A4) is written against `Puzzle.gameId` +
 * the day/puzzleNumber identity surfaced here — records live in their own store
 * (ADR-0004), this repository never owns scores.
 */
interface DailyRepository {

    /** Fetch-today → cache; on any fetch failure degrade to today's cache + the sealed error. */
    suspend fun today(game: GameId): DailyToday

    /** [game]'s captured days, newest first (A8). Never touches the network nor loads payloads. */
    fun observeHistory(game: GameId): Flow<List<DailyEntry>>

    /**
     * [day] of [game]: the cached row, else one fetch when [archiveRange] covers it (ADR-0012).
     * A past day's row the source calls a placeholder is a miss (A10).
     */
    suspend fun day(game: GameId, day: LocalDate): DailyDay

    /** The days [game] can open by date (archiveSince..today), or null when its source has no archive. */
    fun archiveRange(game: GameId): ClosedRange<LocalDate>?

    /**
     * [game]'s captured puzzle for [day], or null; never fetches. Reads Room only: a past
     * day's row the source calls a placeholder is null, the same miss [day] refetches (A10).
     */
    suspend fun cachedPuzzle(game: GameId, day: LocalDate): Puzzle?

    /** [game]'s puzzle number for [day] from its source (A20), or null; never touches the network nor the cache. */
    fun puzzleNumber(game: GameId, day: LocalDate): Int?

    /** [game]'s LinkedIn Games type id from its source, or null for any other game; never touches the network nor the cache. */
    fun linkedInGameTypeId(game: GameId): Int?
}

