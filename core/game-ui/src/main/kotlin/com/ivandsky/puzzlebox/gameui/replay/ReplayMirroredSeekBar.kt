package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val MirroredSeekBarHeight = 96.dp
private const val UNPLAYED_ALPHA = 0.3f

/**
 * The compare's seek strip: my activity above the axis in the primary colour, the other side's below it in
 * tertiary, both over the longer side's play time. A null side draws no bars on its half. The playhead
 * crosses both halves and the whole strip drags.
 */
@Composable
internal fun ReplayMirroredSeekBar(
    positionMs: Long,
    durationMs: Long,
    mineTicksMs: LongArray?,
    theirsTicksMs: LongArray?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val mine = remember(mineTicksMs, durationMs) { mineTicksMs?.let { activityBuckets(it, durationMs) } }
    val theirs = remember(theirsTicksMs, durationMs) { theirsTicksMs?.let { activityBuckets(it, durationMs) } }
    ReplayScrubStrip(positionMs, durationMs, onSeek, MirroredSeekBarHeight, modifier) { fraction ->
        val axis = size.height / 2
        val room = axis - 4.dp.toPx()
        val offset = 1.dp.toPx()
        if (mine != null) {
            drawBarSeries(mine, fraction, axis - offset, room, true, colors.primary, colors.primary.copy(alpha = UNPLAYED_ALPHA))
        }
        if (theirs != null) {
            drawBarSeries(theirs, fraction, axis + offset, room, false, colors.tertiary, colors.tertiary.copy(alpha = UNPLAYED_ALPHA))
        }
    }
}
