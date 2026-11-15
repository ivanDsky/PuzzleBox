package com.ivandsky.puzzlebox.gameui

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
 */
@Composable
fun TimerBar(
    elapsedMs: () -> Long,
    modifier: Modifier = Modifier,
    budgetMs: Long? = null,
) {
    val shownSecond by remember(elapsedMs) { derivedStateOf { elapsedMs() / 1000L } }
    LocalTimerLabelComposed.current()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = formatDuration(shownSecond * 1000L),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (budgetMs != null) BudgetFill(elapsedMs, budgetMs)
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
