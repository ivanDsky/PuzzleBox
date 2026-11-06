package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** A fetched daily: the [Puzzle] envelope plus the fetch-time identity core/data persists (ADR-0004). */
data class DailyFetch(val puzzle: Puzzle, val ref: PuzzleRef)

/**
 * One game's daily feed (ADR-0002 LinkedInDaily): each game module owns its adapter —
 * wire extraction, parsing, trust gate, canonical envelope — and registers it in Koin;
 * the daily repository consumes every registered source keyed by [gameId].
 * [fetch] is the [PuzzleSource] view: the envelope without the identity. Whether past
 * days can be fetched is [archiveSince]'s call (ADR-0012), not [fetch]'s.
 */
interface DailySource : PuzzleSource {

    /** The game this source serves; the repository's key. */
    val gameId: GameId

    /**
     * The first day this source serves by date (ADR-0012), or null when it serves only
     * today (the LinkedIn feeds ignore [fetchDaily]'s date). Non-null: [fetchDaily] honours
     * any date from this day through the game calendar's today.
     */
    val archiveSince: LocalDate? get() = null

    /** Fetches the daily for [date] with its identity, or a typed failure (degrade to cache, A7/A8). */
    suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch>

    override suspend fun fetch(date: LocalDate): FetchResult<Puzzle> =
        when (val result = fetchDaily(date)) {
            is FetchResult.Ok -> FetchResult.Ok(result.value.puzzle)
            is FetchResult.Err -> result
        }
}
