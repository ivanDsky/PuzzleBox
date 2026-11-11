package com.ivandsky.puzzlebox.profile.accounts

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/**
 * The last-update phrase of the LinkedIn row: a coarse relative time, "Updating…" when nothing was ever read.
 * A stored time ahead of [nowMs] (a clock set back) reads as just now rather than as a negative age.
 */
internal fun linkedInUpdatedText(updatedAtMs: Long?, nowMs: Long): String {
    if (updatedAtMs == null) return "Updating…"
    return "Updated " + relativeAgeText(updatedAtMs, nowMs)
}

/** "just now", "12 min ago", "3 hours ago", "1 day ago": the house age phrase, shared by the LinkedIn and Google rows. */
internal fun relativeAgeText(atMs: Long, nowMs: Long): String {
    val age = nowMs - atMs
    return when {
        age < MINUTE_MS -> "just now"
        age < HOUR_MS -> "${age / MINUTE_MS} min ago"
        age < DAY_MS -> plural(age / HOUR_MS, "hour")
        else -> plural(age / DAY_MS, "day")
    }
}

private fun plural(n: Long, unit: String): String = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
