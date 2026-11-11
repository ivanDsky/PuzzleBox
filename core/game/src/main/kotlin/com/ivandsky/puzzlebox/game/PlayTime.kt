package com.ivandsky.puzzlebox.game

import java.time.Duration
import java.time.Instant

/** Elapsed play time now. Pure. Never negative; a clock that ran back counts as 0. */
fun playTimeMs(bankedMs: Long, runningSince: Instant?, now: Instant): Long {
    val windowMs = runningSince?.let { maxOf(0L, Duration.between(it, now).toMillis()) } ?: 0L
    return maxOf(0L, bankedMs) + windowMs
}
