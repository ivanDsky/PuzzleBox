package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val SeekBarHeight = 64.dp
private const val UNPLAYED_ALPHA = 0.28f

/** Activity bars over the replay's play time, a bar per slice taller the more moves land in it. The whole strip drags. */
@Composable
fun ReplaySeekBar(
    positionMs: Long,
    durationMs: Long,
    ticksMs: LongArray,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val counts = remember(ticksMs, durationMs) { activityBuckets(ticksMs, durationMs) }
    ReplayScrubStrip(positionMs, durationMs, onSeek, SeekBarHeight, modifier) { fraction ->
        drawBarSeries(
            counts = counts,
            fraction = fraction,
            axisY = size.height - 6.dp.toPx(),
            room = 52.dp.toPx(),
            up = true,
            played = colors.primary,
            unplayed = colors.onSurfaceVariant.copy(alpha = UNPLAYED_ALPHA),
        )
    }
}
