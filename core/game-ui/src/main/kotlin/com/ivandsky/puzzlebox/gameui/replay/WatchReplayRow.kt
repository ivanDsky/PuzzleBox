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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular

/** The result's way into the replay: a hairline, then play, "Watch replay" over the move count, and the play time. */
@Composable
fun WatchReplayRow(moves: Int, durationMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(Modifier.size(44.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(PuzzleIcons.Play, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(ReplayCopy.WATCH, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(ReplayCopy.moves(moves), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text(formatDuration(durationMs), style = MaterialTheme.typography.bodyMedium.tabular(), color = colors.onSurfaceVariant)
        }
    }
}
