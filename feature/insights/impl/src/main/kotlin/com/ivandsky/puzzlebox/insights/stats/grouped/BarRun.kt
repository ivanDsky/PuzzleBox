package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar

/**
 * One run of a bar chart's motion, read at [elapsedMs] since it started. [bars] are the chart's data (where the run
 * ends), the `...At` functions are what to draw at an instant of it: [scaleAt] the height scale of bar `index`.
 */
internal sealed interface BarRun {
    val bars: List<BarChartBar>
    val totalMs: Int

    fun scaleAt(index: Int, elapsedMs: Float): Float = 1f
    fun valuesAt(elapsedMs: Float): Float = 1f

    /** The value written above bar [index] at [elapsedMs], or null for none. */
    fun valueAt(index: Int, elapsedMs: Float): Double? = bars[index].value

    /** Nothing moves: the first sight of a chart whose arrival is skipped, and every change under reduced motion (an instant swap). */
    class Rest(override val bars: List<BarChartBar>) : BarRun {
        override val totalMs = 0
    }

    /** M04: the bars grow from the baseline one after another, then their values fade in. */
    class Arrival(override val bars: List<BarChartBar>) : BarRun {
        override val totalMs = barGrowthTotalMs(bars.size)
        override fun scaleAt(index: Int, elapsedMs: Float) = barGrowth(index, elapsedMs)
        override fun valuesAt(elapsedMs: Float) = labelAlpha(elapsedMs, totalMs.toFloat())
    }

    /**
     * M05: the bars on screen, as fractions of the tallest ([before], by label), rescale into [bars]; a bar without an
     * old partner grows in. The values above the bars are the old ones ([fromBars], by label; none for a bar with no
     * partner) until the midpoint and the new ones after it, fading out and in around it.
     */
    class Morph(before: Map<String, Float>, override val bars: List<BarChartBar>, fromBars: List<BarChartBar> = emptyList()) : BarRun {
        private val oldValues = fromBars.associate { it.label to it.value }
        override val totalMs = CHART_MORPH_MS
        private val starts = barMorphStarts(before, bars)
        override fun scaleAt(index: Int, elapsedMs: Float) = barMorphScale(starts.getOrElse(index) { 1f }, elapsedMs)
        override fun valuesAt(elapsedMs: Float) = labelSwapAlpha(elapsedMs, totalMs.toFloat())
        override fun valueAt(index: Int, elapsedMs: Float) =
            if (labelsAreOld(elapsedMs, totalMs.toFloat())) oldValues[bars[index].label] else bars[index].value
    }

    /** The bars as drawn at [elapsedMs], as fractions of the tallest of [bars], by label: where a next morph starts from. */
    fun shownFractions(elapsedMs: Float): Map<String, Float> {
        val own = barFractions(bars)
        return bars.mapIndexed { index, bar -> bar.label to own.getValue(bar.label) * scaleAt(index, elapsedMs) }.toMap()
    }
}
