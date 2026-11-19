package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTimerFill
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular

/** Called each time the timer label composes; a no-op outside tests, which count the label's redraws with it. */
internal val LocalTimerLabelComposed = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Elapsed-play readout (F1 §3 "Timer bar + state"). Start-on-first-move and pause-on-
 * background are the GAME SESSION's semantics — the bar only renders the elapsed value it
 * is handed, counting UP against the optional [budgetMs] target (fills toward it, switches
 * to the tertiary hue once over; null budget = open-ended count-up). The digits are
 * tabular so the readout never jitters as it counts.
 *
 * [elapsedMs] is read lazily: the label keeps the whole second it shows and recomposes only
 * when that second changes, not on every clock tick the game's loop pushes.
 *
 * With a [budgetMs] and a [label] the bar counts toward the target instead: [label] writes the readout from whether
 * the whole-second clock has reached the target ([label]'s first argument) and the time left or over as `m:ss`
 * (e.g. [selfTimerLabel]: "0:46 left", "Over by 0:08"). Crossing the target crossfades the label and tweens the fill
 * to the tertiary hue (see [CrossingLabel]); with reduced motion both swap instantly. The default null leaves every
 * caller as it was.
 */
@Composable
fun TimerBar(
    elapsedMs: () -> Long,
    modifier: Modifier = Modifier,
    budgetMs: Long? = null,
    label: ((over: Boolean, clock: String) -> String)? = null,
) {
    val shownSecond by remember(elapsedMs) { derivedStateOf { elapsedMs() / 1000L } }
    LocalTimerLabelComposed.current()
    val style = MaterialTheme.typography.titleMedium.tabular()
    val color = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (budgetMs != null && label != null) {
            val standing by remember(elapsedMs, budgetMs) { derivedStateOf { crossing(elapsedMs(), budgetMs) } }
            CrossingLabel(over = standing.over) { over ->
                Text(text = label(over, standing.clock), style = style, color = color)
            }
            CrossingFill(elapsedMs, budgetMs)
        } else {
            Text(text = formatDuration(shownSecond * 1000L), style = style, color = color)
            if (budgetMs != null) BudgetFill(elapsedMs, budgetMs)
        }
    }
}

/** The budget's fill; it reads the clock itself, so its per-tick redraws never touch the label. */
@Composable
private fun BudgetFill(elapsedMs: () -> Long, budgetMs: Long) {
    val elapsed = elapsedMs()
    val fraction = (elapsed.toFloat() / budgetMs.toFloat()).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape),
        color = if (elapsed > budgetMs) {
            MaterialTheme.colorScheme.tertiary
        } else {
            LocalTimerFill.current
        },
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

/**
 * The fill of a bar counting toward a target: flat, full once over, and crossing on the label's whole seconds so the
 * two change together. It is as wide as the label above it, not the fill's own default width.
 */
@Composable
private fun CrossingFill(elapsedMs: () -> Long, budgetMs: Long) {
    val elapsed = elapsedMs()
    val over = crossing(elapsed, budgetMs).over
    FlatProgressBar(
        progress = { if (over) 1f else (elapsed.toFloat() / budgetMs.toFloat()).coerceIn(0f, 1f) },
        color = rememberCrossingFill(over),
        modifier = Modifier.labelWidth(),
    )
}

/**
 * Fills the width it is given but asks for none when measured for its intrinsic size, so the bar's width follows the
 * label above it (the top bar sizes the timer to its text) instead of the fill's own default width.
 */
private fun Modifier.labelWidth(): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val placeable = measurable.measure(Constraints(width, width, constraints.minHeight, constraints.maxHeight))
    layout(width, placeable.height) { placeable.place(0, 0) }
}
