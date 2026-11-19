package com.ivandsky.puzzlebox.resultui.replay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.resultui.ResultActionRow

/** The Material 3 disabled content alpha. */
private const val DISABLED_ALPHA = 0.38f

/**
 * The result's way into the replay: a hairline, then play, "Watch replay" over the move count, and the play time.
 * A [label] sits as a small overline above "Watch replay". A false [hairline] leaves the line to a group that draws one
 * over several rows.
 */
@Composable
fun WatchReplayRow(
    moves: Int,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hairline: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    WatchReplayRowFrame(modifier, label, hairline, ReplayCopy.moves(moves), Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Text(formatDuration(durationMs), style = MaterialTheme.typography.bodyMedium.tabular(), color = colors.onSurfaceVariant)
    }
}

/**
 * The Watch replay row where a stored solve has no replay: the same layout, disabled, with the [reason] in place of
 * the move count. Not clickable; TalkBack reads one disabled item.
 */
@Composable
internal fun UnavailableWatchReplayRow(
    reason: ReplayUnavailable,
    modifier: Modifier = Modifier,
    label: String? = null,
    hairline: Boolean = true,
) {
    val supporting = when (reason) {
        ReplayUnavailable.ImportedFromLinkedIn -> ReplayCopy.UNAVAILABLE_IMPORTED
        ReplayUnavailable.NotRecorded -> ReplayCopy.UNAVAILABLE
    }
    WatchReplayRowFrame(
        modifier.semantics(mergeDescendants = true) { disabled() },
        label,
        hairline,
        supporting,
        Modifier.alpha(DISABLED_ALPHA),
    ) {}
}

@Composable
private fun WatchReplayRowFrame(
    modifier: Modifier,
    label: String?,
    hairline: Boolean,
    supporting: String,
    rowModifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        if (hairline) HorizontalDivider(color = colors.outlineVariant)
        ResultActionRow(
            modifier = rowModifier.fillMaxWidth(),
            circleColor = colors.primary,
            title = ReplayCopy.WATCH,
            supporting = supporting,
            overline = label,
            trailing = trailing,
        ) {
            Icon(PuzzleIcons.Play, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(ReplaySizes.Icon))
        }
    }
}
