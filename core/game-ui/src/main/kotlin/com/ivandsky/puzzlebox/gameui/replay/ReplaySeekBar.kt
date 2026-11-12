package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val TickHeight = 8.dp
private val TickWidth = 2.dp

// Assumes Material 3's thumb width: a restyled slider must update this inset or the ticks drift (ticket 49).
private val TrackInset = 10.dp

/** Stock slider over the replay's play time, with a quiet tick at every move stamp in [ticksMs]. */
@Composable
fun ReplaySeekBar(
    positionMs: Long,
    durationMs: Long,
    ticksMs: LongArray,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Box(modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().heightIn(min = TickHeight)) {
            if (durationMs <= 0) return@Canvas
            val inset = TrackInset.toPx()
            val span = size.width - 2 * inset
            val centerY = size.height / 2
            for (tickMs in ticksMs) {
                val x = inset + span * (tickMs.toFloat() / durationMs).coerceIn(0f, 1f)
                drawLine(
                    color = tickColor,
                    start = Offset(x, centerY - TickHeight.toPx() / 2),
                    end = Offset(x, centerY + TickHeight.toPx() / 2),
                    strokeWidth = TickWidth.toPx(),
                )
            }
        }
        Slider(
            value = positionMs.coerceIn(0, durationMs).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
            enabled = durationMs > 0,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ReplayTags.SEEK)
                .semantics { contentDescription = "Seek" },
        )
    }
}
