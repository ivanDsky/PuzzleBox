package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Flat page bar: a bare back arrow, then the title over a one-line subtitle, then the page's own [trailing]
 * action. Nothing is boxed or pilled.
 */
@Composable
internal fun ReplayTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(ReplaySizes.TouchTarget)) {
            Icon(PuzzleIcons.Back, contentDescription = ReplayCopy.BACK, tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/** The "Show errors" switch of a replay page: an icon toggle, lit while rule violations show on the boards. */
@Composable
internal fun ShowErrorsToggle(showErrors: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    IconToggleButton(
        checked = showErrors,
        onCheckedChange = onChange,
        modifier = modifier
            .size(ReplaySizes.TouchTarget)
            .testTag(ReplayTags.SHOW_ERRORS)
            .semantics {
                contentDescription = ReplayCopy.SHOW_ERRORS
                stateDescription = if (showErrors) ReplayCopy.ON else ReplayCopy.OFF
            },
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(ReplayIcons.Errors, contentDescription = null, modifier = Modifier.size(ReplaySizes.Icon))
    }
}
