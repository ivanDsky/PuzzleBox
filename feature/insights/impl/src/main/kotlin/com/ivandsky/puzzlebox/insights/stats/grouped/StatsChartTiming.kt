package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar

// The stats chart motion clock. Every curve below is a pure function of the milliseconds since the chart started
// moving, so one linear clock drives a whole chart and a test or a preview can read any instant of it.

/** A line draws in over the expressive duration. */
internal val LINE_DRAW_MS = MotionTokens.DurationExpressive

/** A Since / game change morphs the chart over the shared chart morph duration. */
internal const val CHART_MORPH_MS = MotionTokens.ChartMorphDuration

/**
 * Each bar starts growing this long after the one before it. There is no token for it: it is the stagger of handoff 3a
 * M04 (bars grow from the baseline, 30 ms apart), kept here by name so the bar growth and its tests read it.
 */
internal const val BAR_STAGGER_MS = 30

/** How far the line is drawn, 0..1, [elapsedMs] after the chart arrived (M04: 400 ms on the standard curve). */
internal fun lineDrawProgress(elapsedMs: Float): Float =
    MotionTokens.EasingStandard.transform((elapsedMs / LINE_DRAW_MS).coerceIn(0f, 1f))

/** How far a Since / game change has morphed, 0..1, [elapsedMs] after it. */
internal fun morphProgress(elapsedMs: Float): Float =
    MotionTokens.EasingStandard.transform((elapsedMs / CHART_MORPH_MS).coerceIn(0f, 1f))

/** The axis values and point labels of a first arrival: hidden until the last [MotionTokens.ChartLabelFade] of a [totalMs] run, then faded in. */
internal fun labelAlpha(elapsedMs: Float, totalMs: Float): Float =
    ((elapsedMs - (totalMs - MotionTokens.ChartLabelFade)) / MotionTokens.ChartLabelFade).coerceIn(0f, 1f)

/**
 * The labels of a morph that swaps them: the old ones fade out over the first [MotionTokens.ChartLabelFade] of a
 * [totalMs] run, the new ones in over the last, and the swap is at the midpoint. Never a jump between two frames.
 */
internal fun labelSwapAlpha(elapsedMs: Float, totalMs: Float): Float {
    val fade = MotionTokens.ChartLabelFade
    return if (elapsedMs < totalMs / 2) (1f - elapsedMs / fade).coerceIn(0f, 1f) else labelAlpha(elapsedMs, totalMs)
}

/** The stagger index of an arriving block: only blocks that arrive together (the first pass) wait on each other. */
internal fun arrivalStagger(index: Int, firstPass: Boolean): Int = if (firstPass) index else 0

/** How tall bar [index] stands, 0..1 of its height, [elapsedMs] after the chart arrived (M04, standard curve). */
internal fun barGrowth(index: Int, elapsedMs: Float): Float =
    MotionTokens.EasingStandard.transform(((elapsedMs - index * BAR_STAGGER_MS) / MotionTokens.DurationLong).coerceIn(0f, 1f))

/** The whole arrival of [bars] bars, in milliseconds. */
internal fun barGrowthTotalMs(bars: Int): Int = MotionTokens.DurationLong + BAR_STAGGER_MS * (bars - 1).coerceAtLeast(0)

/** Each bar's height as a fraction of the tallest, keyed by its label; never below 0. */
internal fun barFractions(bars: List<BarChartBar>): Map<String, Float> {
    val tallest = bars.maxOfOrNull { it.value }?.takeIf { it > 0.0 } ?: return bars.associate { it.label to 0f }
    return bars.associate { it.label to (it.value.coerceAtLeast(0.0) / tallest).toFloat() }
}

/**
 * Where each of the [new] bars starts a morph, as a scale of its own new height: a bar that was there starts at its
 * old height (so it travels to the new one), one with no old partner starts at 0 and grows in.
 */
internal fun barMorphStarts(old: Map<String, Float>, new: List<BarChartBar>): List<Float> {
    val fractions = barFractions(new)
    return new.map { bar ->
        val before = old[bar.label] ?: return@map 0f
        val after = fractions.getValue(bar.label)
        if (after == 0f) 1f else before / after
    }
}

/** A morphing bar's scale [elapsedMs] into the morph, from [start] to 1. */
internal fun barMorphScale(start: Float, elapsedMs: Float): Float = start + (1f - start) * morphProgress(elapsedMs)
