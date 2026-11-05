package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle

/** A by-date read's outcome (ADR-0012). */
sealed interface DailyDay {
    /** The day's puzzle, from cache or just fetched and cached. */
    data class Ready(val puzzle: Puzzle, val puzzleNumber: Int?) : DailyDay

    /** Not cached and the source serves only today (LinkedIn games), or the game is unregistered. */
    data object NotCaptured : DailyDay

    /** Before the source's archiveSince or after the game's today. */
    data object OutOfRange : DailyDay

    /** Fetching the day failed; [error] is the source's typed error (Unavailable = offline, NotFound, Untrusted, Throttled). */
    data class Failed(val error: FetchError) : DailyDay
}
