package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

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
 * fetched day is cached in Room. Today is refreshed in the background over its stored puzzle (A7); a past day is
 * read by date (ADR-0012): cache first, and fetched on a miss only when the game's
 * source declares an archive (`DailySource.archiveSince`); without one, past days stay
 * cache-only (A8).
 *
 * The player's record (first in-app completion, A4) is written against `Puzzle.gameId` +
 * the day/puzzleNumber identity surfaced here — records live in their own store
 * (ADR-0004), this repository never owns scores.
 */
interface DailyRepository {

    /**
     * The one shared refresh of today's puzzle for [game]: concurrent callers wait on the same request, and a
     * call within ten minutes of a successful fetch makes none and answers from that fetch (so it is still
     * [DailyRefresh.Changed] while the stored puzzle differs). The fetched puzzle is stored only when none is
     * stored; a stored daily is never overwritten here, only through [replaceToday]. A cancelled caller stops
     * waiting; the request goes on for the others.
     */
    suspend fun refreshToday(game: GameId): DailyRefresh

    /**
     * One transaction: replaces today's row with [fresh] only if the stored puzzle still equals [expected].
     * [fresh] is the puzzle a [DailyRefresh.Changed] carried; any other is refused, so the store only ever
     * holds what the source served.
     */
    suspend fun replaceToday(game: GameId, expected: Puzzle, fresh: Puzzle): Boolean

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

    /** [game]'s LinkedIn route from its source, e.g. `games/zip/`, or null for a game that cannot be posted; never touches the network nor the cache. */
    fun linkedInGamePath(game: GameId): String?
}

