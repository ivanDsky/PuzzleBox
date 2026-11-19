package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.resultui.DuelSideState

/** Whether a puzzle took less, more or the same whole seconds than your first solve of it. */
internal enum class Trend { Quicker, Slower, Same }

/** One puzzle against your first solve: how many whole [seconds] apart, and which way. */
internal data class Improvement(val trend: Trend, val seconds: Long) {
    val text: String
        get() = when (trend) {
            Trend.Quicker -> "$seconds s quicker than your first solve"
            Trend.Slower -> "$seconds s slower than your first solve"
            Trend.Same -> "Same as your first solve"
        }

    /** Positive when quicker, negative when slower. */
    val signedSeconds: Long
        get() = when (trend) {
            Trend.Quicker -> seconds
            Trend.Slower -> -seconds
            Trend.Same -> 0L
        }
}

/**
 * The gap of [mine] to your first solve [past]; null unless both sides have a time. Times are floored to whole seconds
 * first by [marginSeconds], the duel's own margin, so the gap agrees with the figures on screen.
 */
internal fun improvementOf(mine: DuelSideState, past: DuelSideState): Improvement? {
    val a = mine as? DuelSideState.Time ?: return null
    val b = past as? DuelSideState.Time ?: return null
    val gap = marginSeconds(a.timeMs, b.timeMs)
    return when {
        gap == 0L -> Improvement(Trend.Same, 0)
        a.timeMs < b.timeMs -> Improvement(Trend.Quicker, gap)
        else -> Improvement(Trend.Slower, gap)
    }
}

private const val MS_PER_SECOND = 1_000L

/**
 * The summary line under the result, "Faster on 2 of 3 · 0:41 quicker overall", over the puzzles that have a gap; null
 * when none has one. A net of nothing reads "same time overall".
 */
internal fun improvementSummary(gaps: List<Improvement?>): String? {
    val compared = gaps.filterNotNull()
    if (compared.isEmpty()) return null
    val quicker = compared.count { it.trend == Trend.Quicker }
    val net = compared.sumOf { it.signedSeconds }
    val overall = when {
        net > 0 -> "${formatDuration(net * MS_PER_SECOND)} quicker overall"
        net < 0 -> "${formatDuration(-net * MS_PER_SECOND)} slower overall"
        else -> "same time overall"
    }
    return "Faster on $quicker of ${compared.size} · $overall"
}
