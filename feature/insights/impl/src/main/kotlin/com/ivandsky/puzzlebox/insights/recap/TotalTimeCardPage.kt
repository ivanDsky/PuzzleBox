package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MaxTextWidth
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * The Total time page, a day river: the time spent solving as a big "Xh Ym" over one bar for each day of the month (each
 * week of a year). On arrival the bars rise in a wave left to right while the hero climbs with it; a drag along the river
 * reads a day, and lifting it returns to the busiest. Reduced motion shows it all settled, with one short fade.
 */
@Composable
internal fun TotalTimeCardPage(
    card: RecapCard.TotalTime,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val bars = remember(card) { card.riverBars() }
    val scrub = rememberRiverScrub()
    val wave = rememberArrivalWave(card)
    BoxWithConstraints(modifier.fillMaxSize().testTag("recap.totaltime")) {
        val wide = maxWidth >= WideWidth
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = periodLabel,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = LabelSpacing),
                color = LocalContentColor.current,
            )
            Text(
                text = "Time spent solving",
                modifier = Modifier.clearAndSetSemantics { },
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.height(Spacing.sm))
            TotalTimeHero(card.totalMs, wave.progress, wide, Modifier.testTag("recap.totaltime.time").graphicsLayer { alpha = wave.fade() })
            Spacer(Modifier.height(Spacing.lg))
            TotalTimeRiver(
                bars = bars,
                selected = scrub.value,
                color = LocalContentColor.current,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("recap.totaltime.river").graphicsLayer { alpha = wave.fade() },
                wave = wave.progress,
                onScrub = { scrub.value = it },
            )
            AxisRow(card.axisLabels(bars))
            Spacer(Modifier.height(Spacing.md))
            Readout(card.readout(bars, scrub.value), "recap.totaltime.readout", Modifier.fillMaxWidth().heightIn(min = ReadoutHeight))
            Spacer(Modifier.height(Spacing.md))
            TotalTimeFactRows(card, Modifier.widthIn(max = MaxTextWidth))
        }
    }
}

/** The labels under the river: first, middle and last, set apart so the middle one sits near the middle. */
@Composable
private fun AxisRow(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(top = Spacing.xs).clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, style = MaterialTheme.typography.labelMedium.tabular(), color = LocalContentColor.current, maxLines = 1) }
    }
}

/** What the river says about one bar: its kind over its date and "16m · 5 puzzles". The text morphs when the bar changes. */
@Composable
internal fun Readout(readout: RiverReadout?, tag: String, modifier: Modifier = Modifier) {
    if (readout == null) return Spacer(modifier)
    MorphCell(readout, modifier.semantics(mergeDescendants = true) {}.testTag(tag)) {
        Column {
            Text(it.label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = LabelSpacing), color = LocalContentColor.current)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.title, style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(it.detail, style = MaterialTheme.typography.titleMedium.tabular(), color = LocalContentColor.current, maxLines = 1)
            }
        }
    }
}

/** The arrival: [progress] runs the wave 0 to 1 once the card is up; [fade] is the one short fade a reduced-motion player gets instead. */
private class ArrivalWave(val progress: () -> Float, val fade: () -> Float)

@Composable
private fun rememberArrivalWave(card: RecapCard.TotalTime): ArrivalWave {
    val reduced = rememberReducedMotion()
    val preview = entranceSettled()
    val settled = reduced || preview
    val progress = remember(card) { Animatable(if (settled) 1f else 0f) }
    val fade = remember(card) { Animatable(if (reduced && !preview) 0f else 1f) }
    LaunchedEffect(progress, settled) {
        if (settled) progress.snapTo(1f) else progress.animateTo(1f, tween(WaveMs.toInt(), delayMillis = ArrivalDelayMs, easing = LinearEasing))
    }
    LaunchedEffect(fade, reduced) {
        if (reduced) fade.animateTo(1f, tween(MotionTokens.ReducedFadeDuration)) else fade.snapTo(1f)
    }
    return remember(progress, fade) { ArrivalWave({ progress.value }, { fade.value }) }
}

/** The puzzles the time went on and the average per puzzle as one rounded block. Shared by the story and the tablet list. */
@Composable
internal fun TotalTimeFactRows(card: RecapCard.TotalTime, modifier: Modifier = Modifier) {
    RecapFactRows(
        rows = listOf(
            RecapFactRow("recap.totaltime.solved", "Puzzles solved", card.solved.toString()),
            RecapFactRow("recap.totaltime.average", "Average per puzzle", formatDuration(card.totalMs / card.solved)),
        ),
        tag = "recap.totaltime.facts",
        modifier = modifier,
    )
}

/** Hand-held pause before the wave leaves, so the card has settled in first; the motion tokens name no such delay. */
private const val ArrivalDelayMs = 250
private val WideWidth = 600.dp
private val ReadoutHeight = 44.dp
private val LabelSpacing = 1.2.sp
