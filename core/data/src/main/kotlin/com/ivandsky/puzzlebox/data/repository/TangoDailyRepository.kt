package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * UI-state contract for "today's Tango" — the [ZipToday] shape applied to Tango
 * (A7/A8): [Fresh] is a new official daily; [Cached] is today's previously captured
 * puzzle plus the sealed fetch error (banner state, board still playable);
 * [Unavailable] is nothing captured yet.
 */
sealed interface TangoToday {
    data class Fresh(val puzzle: Puzzle) : TangoToday
    data class Cached(val puzzle: Puzzle, val error: FetchError) : TangoToday
    data class Unavailable(val error: FetchError) : TangoToday
}

/** History row for the captured-day list (mirror of [ZipDailyEntry]). */
data class TangoDailyEntry(
    val day: LocalDate,
    val puzzleNumber: Int?,
    val variant: String,
    val fetchedAtMs: Long,
)

/**
 * What the repository consumes from a daily source: the [PuzzleSource] result plus the
 * fetch-time identity (ADR-0004). Production adapter: [LinkedInTangoDailySource].
 */
interface TangoDailySource {
    data class Daily(val puzzle: Puzzle, val ref: TangoPuzzleRef)

    suspend fun fetchDaily(date: LocalDate): FetchResult<Daily>
}

/**
 * Tango compete loop data: fetch is anonymous LinkedIn (A7); every fetched day is
 * cached in Room; history and replay are cache-only (A8 — LinkedIn serves today only).
 */
interface TangoDailyRepository {

    /** Fetch-today → cache; on any fetch failure degrade to today's cache + the sealed error. */
    suspend fun today(): TangoToday

    /** Cache-only read for replay; null when the day wasn't captured. */
    suspend fun cached(day: LocalDate): Puzzle?

    /** Captured days, newest first (A8). Never touches the network. */
    fun observeHistory(): Flow<List<TangoDailyEntry>>
}
