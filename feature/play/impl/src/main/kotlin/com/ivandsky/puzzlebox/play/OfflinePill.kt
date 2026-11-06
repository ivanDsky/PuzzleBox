package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The quiet offline note (06-a §States): saved puzzles still open, only new days wait. */
@Composable
internal fun OfflinePill(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .testTag("library.offline")
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
            .padding(horizontal = Spacing.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PuzzleIcons.NeedsConnection, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
        Text("Offline · saved puzzles still play", style = MaterialTheme.typography.labelMedium, color = ink)
    }
}
