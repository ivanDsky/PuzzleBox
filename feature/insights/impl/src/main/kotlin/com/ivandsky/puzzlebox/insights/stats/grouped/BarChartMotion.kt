package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar

/**
 * The motion state of one bar chart: the [run] on screen and the [clock] it is read at. A chart that starts with
 * [animateArrival] grows; after that, new data morphs from the bars as drawn at that moment or, under reduced motion,
 * swaps at once (M08: no movement; the arrival's own fade is the block's).
 */
internal class BarChartMotion(bars: List<BarChartBar>, animateArrival: Boolean) {
    val clock = ChartClock()
    var run: BarRun by mutableStateOf(if (animateArrival) BarRun.Arrival(bars) else BarRun.Rest(bars))
        private set

    /** Shows [bars]: carries on with the run on screen when it is for the same data, starts the next one otherwise. */
    suspend fun show(bars: List<BarChartBar>, reduced: Boolean) {
        if (bars != run.bars) {
            val before = run.shownFractions(clock.ms)
            run = if (reduced) BarRun.Rest(bars) else BarRun.Morph(before, bars, run.bars)
            clock.restart()
        }
        clock.playTo(run.totalMs)
    }
}
