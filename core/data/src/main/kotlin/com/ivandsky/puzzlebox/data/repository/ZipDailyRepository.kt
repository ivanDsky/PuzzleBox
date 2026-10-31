package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * UI-state contract for "today's Zip" (tickets 13/17 consume this; A7/A8). The fetch
 * outcome and the cache are combined so the UI never has to: [Fresh] is a new official
 * daily; [Cached] is today's previously captured puzzle plus the sealed fetch error
 * (banner state, board still playable); [Unavailable] is nothing captured yet.
 */
sealed interface ZipToday {
    data class Fresh(val puzzle: Puzzle) : ZipToday
    data class Cached(val puzzle: Puzzle, val error: FetchError) : ZipToday
    data class Unavailable(val error: FetchError) : ZipToday
}

/** History row (ticket 14: Paging3 list over these; streak/analytics teaser data). */
data class ZipDailyEntry(
    val day: LocalDate,
    val puzzleNumber: Int?,
    val variant: String,
    val fetchedAtMs: Long,
)

/**
 * What the repository consumes from a daily source: the [PuzzleSource] result plus the
 * fetch-time identity (ADR-0004 — core/data persists date + edition number). Production
 * adapter: [LinkedInZipDailySource] (ADR-0002 LinkedInDaily, which also implements
 * `PuzzleSource` for the archive seam).
 */
interface ZipDailySource {
    data class Daily(val puzzle: Puzzle, val ref: ZipPuzzleRef)

    suspend fun fetchDaily(date: LocalDate): FetchResult<Daily>
}

/**
 * Zip compete loop data (Phase 1b). Fetch is anonymous LinkedIn (A7); every fetched day
 * is cached in Room; history and replay are cache-only (A8 — LinkedIn serves today only).
 *
 * Ticket 15 seam: the player's record (first in-app completion, A4) is written against
 * `Puzzle.gameId` + the day/puzzleNumber identity surfaced here — records live in their
 * own store (ADR-0004), this repository never owns scores.
 */
interface ZipDailyRepository {

    /** Fetch-today → cache; on any fetch failure degrade to today's cache + the sealed error. */
    suspend fun today(): ZipToday

    /** Cache-only read for replay (ticket 14 deep-links here); null when the day wasn't captured. */
    suspend fun cached(day: LocalDate): Puzzle?

    /** Captured days, newest first (A8). Never touches the network. */
    fun observeHistory(): Flow<List<ZipDailyEntry>>
}
