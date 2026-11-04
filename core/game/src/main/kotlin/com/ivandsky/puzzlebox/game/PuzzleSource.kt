package com.ivandsky.puzzlebox.game

import java.time.LocalDate

/**
 * Where puzzles come from (F1 §2). Implementations live outward of core: the LinkedIn
 * daily adapter (ticket 12) and Strands' NYT adapter (Phase 3) in core/data, the in-memory
 * test fake in core:testing, the games archive later (ADR-0002, purely additive).
 */
interface PuzzleSource {
    /** Fetches the puzzle for [date], or a typed failure — fetch callers degrade to cache (A7/A8). */
    suspend fun fetch(date: LocalDate): FetchResult<Puzzle>
}

sealed interface FetchResult<out T> {
    data class Ok<T>(val value: T) : FetchResult<T>
    data class Err(val error: FetchError) : FetchResult<Nothing>

    fun getOrNull(): T? = (this as? Ok)?.value
}

sealed interface FetchError {
    /** No puzzle reachable for the date: network/server/parsing. Degrade to cache (A7/A8). */
    data class Unavailable(val detail: String? = null) : FetchError

    /**
     * LinkedIn's per-IP/edge throttle (R-19-1): an empty 200 with a `location` soft-redirect,
     * ~100 s cooldown. Retryable after a backoff — deliberately not [Unavailable], which
     * reads as "no data".
     */
    data class Throttled(val detail: String? = null) : FetchError

    /** Payload arrived but the engine could not trust it (A16 oracle gate failed). Never playable. */
    data class Untrusted(val detail: String) : FetchError
}
