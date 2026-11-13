package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Where the Watch replay row would be, a quiet non-interactive line saying why a stored solve has no replay. */
@Composable
internal fun ReplayUnavailableLine(reason: ReplayUnavailable, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(ReplayIcons.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                when (reason) {
                    ReplayUnavailable.ImportedFromLinkedIn -> ReplayCopy.UNAVAILABLE_IMPORTED
                    ReplayUnavailable.NotRecorded -> ReplayCopy.UNAVAILABLE
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
