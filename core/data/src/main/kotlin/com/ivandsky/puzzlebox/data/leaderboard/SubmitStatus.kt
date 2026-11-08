package com.ivandsky.puzzlebox.data.leaderboard

/**
 * What happened to this player's daily post for one puzzle, as [SubmitStatusTracker] tells
 * it. A sibling of [com.ivandsky.puzzlebox.data.model.PostState], not an extension of it:
 * PostState is stored by name on received friend puzzles and has no split between a post
 * that will retry and one that never will, so widening it would change what friend boards
 * persist and read.
 */
enum class SubmitStatus {
    /** In flight, or not tried yet. */
    Posting,

    /** The last try failed for a retryable reason; the next records change or app start tries again (A7). */
    Deferred,

    /** The backend refused the entry; final, never retried. */
    Rejected,

    /** The board holds the entry: written, or already there (deduped). */
    Posted,
}
