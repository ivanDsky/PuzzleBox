package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular

private val SPEEDS = intArrayOf(1, 2, 4, 8)
private val ReadoutHeight = 32.dp
private val ControlGap = Spacing.sm
private val RowHeight = 56.dp
private val TouchTarget = 48.dp

/** The controls' height around a seek strip of [stripHeight], so a page can size its board around them without measuring. */
internal fun replayControlsHeight(stripHeight: Dp): Dp = ReadoutHeight + ControlGap + stripHeight + ControlGap + RowHeight

/** The single-board transport's fixed height. */
internal val ReplayTransportHeight: Dp = replayControlsHeight(SeekBarHeight)

/**
 * Flat transport: the position readout over the activity bars, then Skip idle, previous / play / next and the
 * speed in one row. No container: it sits straight on the page.
 */
@Composable
fun ReplayTransport(state: ReplayPlaybackState, playback: ReplayPlayback, modifier: Modifier = Modifier) {
    ReplayControls(state, playback, SeekBarHeight, modifier) {
        ReplaySeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            ticksMs = state.ticksMs,
            onSeek = playback::seekTo,
        )
    }
}

/** The transport's frame around any seek strip of [stripHeight]; [legend] sits at the readout's end. */
@Composable
internal fun ReplayControls(
    state: ReplayPlaybackState,
    playback: ReplayPlayback,
    stripHeight: Dp,
    modifier: Modifier = Modifier,
    legend: @Composable () -> Unit = {},
    strip: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().height(replayControlsHeight(stripHeight)),
        verticalArrangement = Arrangement.spacedBy(ControlGap),
    ) {
        Row(Modifier.fillMaxWidth().height(ReadoutHeight), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Readout(state.positionMs, state.durationMs)
            }
            legend()
        }
        strip()
        Row(Modifier.fillMaxWidth().height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                SkipIdleButton(state.skipIdle) { playback.setSkipIdle(it) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(ReplayCopy.PREVIOUS_MOVE, ReplayTags.PREVIOUS, ReplayIcons.Previous, playback::previousMove)
                PlayButton(state.playing, onClick = if (state.playing) playback::pause else playback::play)
                StepButton(ReplayCopy.NEXT_MOVE, ReplayTags.NEXT, ReplayIcons.Next, playback::nextMove)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                SpeedButton(state.speed) { playback.setSpeed(SPEEDS[(SPEEDS.indexOf(state.speed) + 1) % SPEEDS.size]) }
            }
        }
    }
}

@Composable
private fun Readout(positionMs: Long, durationMs: Long) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.onSurface)) { append(formatDuration(positionMs)) }
            withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(" / ${formatDuration(durationMs)}") }
        },
        style = MaterialTheme.typography.titleLarge.tabular(),
        modifier = Modifier.testTag(ReplayTags.POSITION),
    )
}

@Composable
private fun SkipIdleButton(on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = TouchTarget)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(end = Spacing.md)
            .testTag(ReplayTags.SKIP_IDLE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (on) Icon(PuzzleIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(
            text = ReplayCopy.SKIP_IDLE,
            style = MaterialTheme.typography.labelLarge,
            color = if (on) colors.primary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeedButton(speed: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .defaultMinSize(minWidth = TouchTarget, minHeight = TouchTarget)
            .testTag(ReplayTags.SPEED)
            .semantics { contentDescription = ReplayCopy.speedDescription(speed) },
    ) {
        Text(ReplayCopy.speed(speed), style = MaterialTheme.typography.labelLarge.tabular(), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StepButton(description: String, tag: String, icon: ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(TouchTarget)
            .testTag(tag)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(RowHeight)
            .testTag(ReplayTags.PLAY)
            .semantics { contentDescription = if (playing) ReplayCopy.PAUSE else ReplayCopy.PLAY },
    ) {
        Icon(if (playing) ReplayIcons.Pause else PuzzleIcons.Play, contentDescription = null, modifier = Modifier.size(28.dp))
    }
}
