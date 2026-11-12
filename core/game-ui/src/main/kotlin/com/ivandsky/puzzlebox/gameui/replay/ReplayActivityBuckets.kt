package com.ivandsky.puzzlebox.gameui.replay

internal const val ACTIVITY_BUCKETS = 48

/** Moves per equal slice of the replay: one series of the activity bars. A second side feeds its own ticks. */
internal fun activityBuckets(ticksMs: LongArray, durationMs: Long): IntArray {
    val counts = IntArray(ACTIVITY_BUCKETS)
    if (durationMs <= 0) return counts
    for (tickMs in ticksMs) {
        counts[(tickMs.toFloat() / durationMs * ACTIVITY_BUCKETS).toInt().coerceIn(0, ACTIVITY_BUCKETS - 1)]++
    }
    return counts
}
