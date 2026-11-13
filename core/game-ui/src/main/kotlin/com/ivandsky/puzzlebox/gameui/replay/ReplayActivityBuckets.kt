package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.game.replay.MoveKind

internal const val ACTIVITY_BUCKETS = 48

/**
 * Moves per equal slice of the replay, split by kind: `[kind.ordinal][slice]`. One series of the activity bars;
 * a second side feeds its own ticks. [kindsAt] runs parallel to [ticksMs].
 */
internal fun activityBuckets(ticksMs: LongArray, kindsAt: Array<MoveKind>, durationMs: Long): Array<IntArray> {
    val counts = Array(MoveKind.entries.size) { IntArray(ACTIVITY_BUCKETS) }
    if (durationMs <= 0) return counts
    for (index in ticksMs.indices) {
        val bucket = (ticksMs[index].toFloat() / durationMs * ACTIVITY_BUCKETS).toInt().coerceIn(0, ACTIVITY_BUCKETS - 1)
        counts[kindsAt[index].ordinal][bucket]++
    }
    return counts
}
