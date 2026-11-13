package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular

/** The Material 3 disabled content alpha. */
private const val DISABLED_ALPHA = 0.38f

/**
 * The result's way into the replay: a hairline, then play, "Watch replay" over the move count, and the play time.
 * A [label] sits as a small overline above "Watch replay".
 */
@Composable
fun WatchReplayRow(moves: Int, durationMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val colors = MaterialTheme.colorScheme
    WatchReplayRowFrame(modifier, label, ReplayCopy.moves(moves), Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Text(formatDuration(durationMs), style = MaterialTheme.typography.bodyMedium.tabular(), color = colors.onSurfaceVariant)
    }
}

/**
 * The Watch replay row where a stored solve has no replay: the same layout, disabled, with the [reason] in place of
 * the move count. Not clickable; TalkBack reads one disabled item.
 */
@Composable
internal fun UnavailableWatchReplayRow(reason: ReplayUnavailable, modifier: Modifier = Modifier, label: String? = null) {
    val supporting = when (reason) {
        ReplayUnavailable.ImportedFromLinkedIn -> ReplayCopy.UNAVAILABLE_IMPORTED
        ReplayUnavailable.NotRecorded -> ReplayCopy.UNAVAILABLE
    }
    WatchReplayRowFrame(
        modifier.semantics(mergeDescendants = true) { disabled() },
        label,
        supporting,
        Modifier.alpha(DISABLED_ALPHA),
    ) {}
}

@Composable
private fun WatchReplayRowFrame(
    modifier: Modifier,
    label: String?,
    supporting: String,
    rowModifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(Modifier.size(44.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(PuzzleIcons.Play, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                Text(ReplayCopy.WATCH, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            trailing()
        }
    }
}
