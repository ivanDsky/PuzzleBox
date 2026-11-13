package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession

/**
 * Downgrades transient Mistakes in [kinds] as stamp groups close. One pass; the pending group lives
 * in fields, so a fold allocates nothing per group.
 */
internal class TimelineSettleFilter<S : GameSession>(private val replay: GameReplay<S>, private val kinds: MutableList<MoveKind>) {
    private var pendingStart = 0
    private var pendingEnd = 0
    private var pendingBefore: S? = null
    private var pendingAfter: S? = null

    /**
     * The stamp group `start until end` is complete: [before] and [after] bracket it, and the next group
     * starts at [nextAtMs] ([NO_NEXT] = none). Settles the pending Mistake group, whose next group this was.
     */
    fun closed(start: Int, end: Int, atMs: Long, before: S, after: S, nextAtMs: Long) {
        val pendingBefore = pendingBefore
        if (pendingBefore != null) {
            if (replay.isTransientMistake(pendingBefore, checkNotNull(pendingAfter), after)) downgrade(pendingStart, pendingEnd)
            this.pendingBefore = null
            pendingAfter = null
        }
        if (nextAtMs != NO_NEXT && nextAtMs - atMs < Timeline.MISTAKE_SETTLE_MS && isMistake(start, end)) {
            pendingStart = start
            pendingEnd = end
            this.pendingBefore = before
            pendingAfter = after
        }
    }

    /**
     * The group `start until end` was folded into the stamp group that follows it: [before] and [after]
     * bracket it, and [next] is the board after the first move of that group. A Mistake the game calls
     * transient over that span leaves it, as it would have left the next group's settling.
     */
    fun mergedIntoNext(start: Int, end: Int, before: S, after: S, next: S) {
        if (isMistake(start, end) && replay.isTransientMistake(before, after, next)) downgrade(start, end)
    }

    private fun isMistake(start: Int, end: Int): Boolean {
        for (index in start until end) if (kinds[index] == MoveKind.Mistake) return true
        return false
    }

    private fun downgrade(start: Int, end: Int) {
        var best = MoveKind.Normal
        for (index in start until end) if (kinds[index] != MoveKind.Mistake) best = maxOf(best, kinds[index])
        for (index in start until end) if (kinds[index] == MoveKind.Mistake) kinds[index] = best
    }

    companion object {
        const val NO_NEXT = Long.MAX_VALUE
    }
}
