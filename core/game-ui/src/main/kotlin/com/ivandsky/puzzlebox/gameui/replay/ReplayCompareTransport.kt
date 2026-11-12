package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.replay.Timeline

/** The compare's controls height: the mirrored seek strip is taller than the single board's. */
internal val CompareTransportHeight = replayControlsHeight(MirroredSeekBarHeight)

/**
 * One transport for both sides of a compare: the readout with a name legend, the mirrored seek strip, then the
 * same buttons as the single board. A side without a replay ([mine] or [theirs] null) draws no bars and has no legend entry.
 */
@Composable
internal fun ReplayCompareTransport(
    state: ReplayPlaybackState,
    playback: ReplayPlayback,
    mine: Timeline?,
    theirs: Timeline?,
    names: CompareNames,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    ReplayControls(
        state = state,
        playback = playback,
        stripHeight = MirroredSeekBarHeight,
        modifier = modifier,
        legend = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                if (mine != null) LegendEntry(names.mine, colors.primary)
                if (theirs != null) LegendEntry(names.theirs, colors.tertiary)
            }
        },
    ) {
        ReplayMirroredSeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            mineTicksMs = mine?.moveTimesMs,
            theirsTicksMs = theirs?.moveTimesMs,
            onSeek = playback::seekTo,
        )
    }
}

@Composable
private fun LegendEntry(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
