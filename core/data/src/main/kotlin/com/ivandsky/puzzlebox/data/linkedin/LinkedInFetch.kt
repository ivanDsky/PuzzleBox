package com.ivandsky.puzzlebox.data.linkedin

/** Persisted by `name`, like `ThemeMode` (the `fetch_log.item` column). */
internal enum class LinkedInItem { Own, Board }

/**
 * Persisted by `name`. `BoardToday` marks a board read while its day was still today (delta 0):
 * partial, so it is read once more after the day ends.
 */
internal enum class LinkedInOutcome { Solved, NotPlayed, Board, BoardToday }

/** The last fetch recorded for one (game, day, item, profile). */
internal class LinkedInFetch(val fetchedAtMs: Long, val outcome: LinkedInOutcome)
