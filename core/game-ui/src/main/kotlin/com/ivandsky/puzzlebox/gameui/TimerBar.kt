package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTimerFill
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular

/**
 * Elapsed-play readout (F1 §3 "Timer bar + state"). Start-on-first-move and pause-on-
 * background are the GAME SESSION's semantics — the bar only renders the elapsed value it
 * is handed, counting UP against the optional [budgetMs] target (fills toward it, switches
 * to the tertiary hue once over; null budget = open-ended count-up). The digits are
 * tabular so the readout never jitters as it counts.
 */
@Composable
fun TimerBar(
    elapsedMs: Long,
    modifier: Modifier = Modifier,
    budgetMs: Long? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = formatDuration(elapsedMs),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (budgetMs != null) {
            val fraction = (elapsedMs.toFloat() / budgetMs.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (elapsedMs > budgetMs) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    LocalTimerFill.current
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
