package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint

// Animation Preview for the stats calm motion (M02 to M05, M08): each preview is a stateless frame read at a time that an
// updateTransition plays from 0 to the end of the run, so the Animation Preview pane plays and scrubs it.

private val Week = listOf(95_000.0, 88_000.0, 102_000.0, 76_000.0, 81_000.0, 70_000.0)
private val Month = listOf(110_000.0, 104_000.0, 99_000.0, 93_000.0, 96_000.0, 90_000.0, 84_000.0, 79_000.0, 75_000.0)
private val Days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun line(values: List<Double>) = values.mapIndexed { index, ms -> LineChartPoint(index.toDouble(), ms, "$index") }

private fun bars(values: List<Double>) = values.mapIndexed { index, ms -> BarChartBar(Days[index], ms) }

private fun seconds(value: Double) = "${(value / 1000).toInt()}s"

/** Plays [totalMs] of a run on a linear clock once the preview is up, and hands the chart the time it is at. */
@Composable
private fun Played(totalMs: Int, frame: @Composable (elapsedMs: () -> Float) -> Unit) {
    var playing by remember { mutableStateOf(false) }
    val transition = updateTransition(playing, label = "stats motion")
    val elapsed by transition.animateFloat(transitionSpec = { tween(totalMs, easing = LinearEasing) }, label = "elapsed ms") { end ->
        if (end) totalMs.toFloat() else 0f
    }
    LaunchedEffect(Unit) { playing = true }
    frame { elapsed }
}

@Composable
private fun Stage(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(16.dp)) { content() } }
    }
}

@PuzzleBoxPreviews
@Composable
private fun StatsLineDrawInPreview() = Stage {
    val run = LineRun.Arrival(line(Week), lineScale(line(Week), null))
    Played(run.totalMs) { elapsed -> StatsLineChartAt({ run }, elapsed, "Average solve time", ::seconds) }
}

@PuzzleBoxPreviews
@Composable
private fun StatsBarsGrowPreview() = Stage {
    val run = BarRun.Arrival(bars(Week))
    Played(run.totalMs) { elapsed -> StatsBarChartAt({ run }, elapsed, "Average solve time by weekday", ::seconds) }
}

@PuzzleBoxPreviews
@Composable
private fun StatsLineMorphPreview() = Stage {
    val from = line(Week)
    val to = line(Month)
    val run = LineRun.Morph(from, lineScale(from, null), to, lineScale(to, null))
    Played(run.totalMs) { elapsed -> StatsLineChartAt({ run }, elapsed, "Average solve time", ::seconds) }
}

@PuzzleBoxPreviews
@Composable
private fun StatsBarsMorphPreview() = Stage {
    val before = bars(Week)
    val run = BarRun.Morph(barFractions(before), bars(Month.take(Days.size)))
    Played(run.totalMs) { elapsed -> StatsBarChartAt({ run }, elapsed, "Average solve time by weekday", ::seconds) }
}

@PuzzleBoxPreviews
@Composable
private fun StatsCountUpPreview() = Stage {
    Played(MotionTokens.CountUpDuration) { elapsed ->
        val progress = MotionTokens.EasingEmphasizedDecelerate.transform((elapsed() / MotionTokens.CountUpDuration).coerceIn(0f, 1f))
        Text(countUpText("1,284 solved · 12:48 best", progress), style = MaterialTheme.typography.headlineSmall)
    }
}

@PuzzleBoxPreviews
@Composable
private fun StatsHeadlineMorphPreview() = Stage {
    var since by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { since = true }
    CompositionLocalProvider(LocalStatsMotion provides StatsMotion()) {
        StatsCountUpText(if (since) "1,284" else "312", key = "preview.count", style = MaterialTheme.typography.headlineSmall)
        StatsMorphText(if (since) "Since the start" else "Last 30 days", style = MaterialTheme.typography.bodyMedium)
    }
}

/** M08: with reduced motion a change swaps the chart at once, at the midpoint of the preview: nothing draws in, grows, moves, scales or fades. */
@PuzzleBoxPreviews
@Composable
private fun StatsReducedMotionSwapPreview() = Stage {
    val before = line(Week)
    val after = line(Month)
    val lineBefore = LineRun.Rest(before, lineScale(before, null))
    val lineAfter = LineRun.Rest(after, lineScale(after, null))
    val barsBefore = BarRun.Rest(bars(Week))
    val barsAfter = BarRun.Rest(bars(Month.take(Days.size)))
    Played(1_000) { elapsed ->
        val swapped = elapsed() >= 500f
        StatsLineChartAt({ if (swapped) lineAfter else lineBefore }, elapsed, "Average solve time", ::seconds)
        StatsBarChartAt({ if (swapped) barsAfter else barsBefore }, elapsed, "Average solve time by weekday", ::seconds)
    }
}
