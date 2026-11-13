package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.MoveKind

internal val SeekBarHeight = 64.dp
/**
 * Activity bars over the replay's play time, a bar per slice taller the more moves land in it, stacked by move kind
 * ([tickKinds] parallel to [ticksMs]). The whole strip drags.
 */
@Composable
fun ReplaySeekBar(
    positionMs: Long,
    durationMs: Long,
    ticksMs: LongArray,
    tickKinds: Array<MoveKind>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val counts = remember(ticksMs, tickKinds, durationMs) { activityBuckets(ticksMs, tickKinds, durationMs) }
    val played = remember(colors) { kindColors(colors) }
    val unplayed = remember(colors) { unplayedKindColors(colors, normal = colors.onSurfaceVariant) }
    ReplayScrubStrip(positionMs, durationMs, onSeek, SeekBarHeight, modifier) { fraction ->
        drawBarSeries(
            counts = counts,
            fraction = fraction,
            axisY = size.height - 6.dp.toPx(),
            room = 52.dp.toPx(),
            up = true,
            played = played,
            unplayed = unplayed,
        )
    }
}
