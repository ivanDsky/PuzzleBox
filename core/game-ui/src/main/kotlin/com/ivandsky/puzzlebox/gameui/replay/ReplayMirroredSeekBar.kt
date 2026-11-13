package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.MoveKind

internal val MirroredSeekBarHeight = 96.dp

/**
 * The compare's seek strip: my activity above the axis, the other side's below it, both over the longer side's
 * play time and coloured by move kind ([mineKinds] and [theirsKinds] parallel to their ticks). A null side draws no bars on its half. The playhead
 * crosses both halves and the whole strip drags.
 */
@Composable
internal fun ReplayMirroredSeekBar(
    positionMs: Long,
    durationMs: Long,
    mineTicksMs: LongArray?,
    mineKinds: Array<MoveKind>?,
    theirsTicksMs: LongArray?,
    theirsKinds: Array<MoveKind>?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val mine = remember(mineTicksMs, mineKinds, durationMs) {
        if (mineTicksMs != null && mineKinds != null) activityBuckets(mineTicksMs, mineKinds, durationMs) else null
    }
    val theirs = remember(theirsTicksMs, theirsKinds, durationMs) {
        if (theirsTicksMs != null && theirsKinds != null) activityBuckets(theirsTicksMs, theirsKinds, durationMs) else null
    }
    val played = remember(colors) { kindColors(colors) }
    val unplayed = remember(colors) { unplayedKindColors(colors) }
    ReplayScrubStrip(positionMs, durationMs, onSeek, MirroredSeekBarHeight, modifier) { fraction ->
        val axis = size.height / 2
        val room = axis - 4.dp.toPx()
        val offset = 1.dp.toPx()
        if (mine != null) {
            drawBarSeries(mine, fraction, axis - offset, room, true, played, unplayed)
        }
        if (theirs != null) {
            drawBarSeries(theirs, fraction, axis + offset, room, false, played, unplayed)
        }
    }
}
