package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt

/**
 * One linear clock in milliseconds that drives a whole chart. The curves are pure functions of the milliseconds
 * (see [lineDrawProgress], [barGrowth]) and the chart runs read them off it; the clock only counts, so a chart can be
 * read at any instant.
 */
internal class ChartClock(start: Float = 0f) {
    private val elapsed = Animatable(start)

    /** Milliseconds into the current run. */
    val ms: Float get() = elapsed.value

    suspend fun restart() {
        elapsed.snapTo(0f)
    }

    /** Runs the clock on to [totalMs] at real speed from wherever it stands. */
    suspend fun playTo(totalMs: Int) {
        val left = (totalMs - elapsed.value).roundToInt()
        if (left > 0) elapsed.animateTo(totalMs.toFloat(), tween(left, easing = LinearEasing)) else elapsed.snapTo(totalMs.toFloat())
    }
}
