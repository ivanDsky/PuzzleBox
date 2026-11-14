package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.MoveKind

internal val MirroredSeekBarHeight = 96.dp

/**
 * The compare's seek strip: my activity above the axis, the other side's below it, both over the longer side's
 * play time and coloured by move kind ([mineKinds] and [theirsKinds] parallel to their ticks). Normal wears the
 * side's tint (primary mine, tertiary theirs); the other kinds read the same on both halves. A null side draws
 * no bars on its half. The playhead crosses both halves and the whole strip drags.
 */
@Composable
internal fun ReplayMirroredSeekBar(
    positionMs: () -> Long,
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
    val minePlayed = remember(colors) { kindColors(colors, normal = colors.primary) }
    val mineUnplayed = remember(colors) { unplayedKindColors(colors, normal = colors.primary) }
    val theirsPlayed = remember(colors) { kindColors(colors, normal = colors.tertiary) }
    val theirsUnplayed = remember(colors) { unplayedKindColors(colors, normal = colors.tertiary) }
    ReplayScrubStrip(positionMs, durationMs, onSeek, MirroredSeekBarHeight, modifier) {
        val axis = size.height / 2
        val room = axis - 4.dp.toPx()
        val offset = 1.dp.toPx()
        val mineShapes = mine?.let { barSeriesShapes(it, size.width, axis - offset, room, up = true) }
        val theirsShapes = theirs?.let { barSeriesShapes(it, size.width, axis + offset, room, up = false) }
        val draw: DrawScope.(needleX: Float) -> Unit = { needleX ->
            if (mineShapes != null) drawBarSeries(mineShapes, needleX, minePlayed, mineUnplayed)
            if (theirsShapes != null) drawBarSeries(theirsShapes, needleX, theirsPlayed, theirsUnplayed)
        }
        draw
    }
}
