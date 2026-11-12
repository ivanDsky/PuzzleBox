package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular

private val SPEEDS = intArrayOf(1, 2, 4, 8)

/** Play/pause, speed 1×/2×/4×/8×, skip idle, previous/next move, seek bar with ticks at state.ticksMs. */
@Composable
fun ReplayTransport(state: ReplayPlaybackState, playback: ReplayPlayback, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ReplaySeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            ticksMs = state.ticksMs,
            onSeek = playback::seekTo,
        )
        Text(
            text = "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}",
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ReplayTags.POSITION),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton("Previous move", ReplayTags.PREVIOUS, "⏮", playback::previousMove)
            ControlButton(
                description = if (state.playing) "Pause" else "Play",
                tag = ReplayTags.PLAY,
                label = if (state.playing) "⏸" else "▶",
                onClick = if (state.playing) playback::pause else playback::play,
            )
            ControlButton("Next move", ReplayTags.NEXT, "⏭", playback::nextMove)
            ControlButton(
                description = "Speed ${state.speed}×",
                tag = ReplayTags.SPEED,
                label = "${state.speed}×",
                onClick = { playback.setSpeed(SPEEDS[(SPEEDS.indexOf(state.speed) + 1) % SPEEDS.size]) },
            )
            FilterChip(
                selected = state.skipIdle,
                onClick = { playback.setSkipIdle(!state.skipIdle) },
                label = { Text("Skip idle") },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(ReplayTags.SKIP_IDLE)
                    .semantics { contentDescription = "Skip idle" },
            )
        }
    }
}

@Composable
private fun ControlButton(description: String, tag: String, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            .testTag(tag)
            .semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
