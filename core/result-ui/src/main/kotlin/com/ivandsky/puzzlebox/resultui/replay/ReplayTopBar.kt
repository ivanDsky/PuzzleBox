package com.ivandsky.puzzlebox.resultui.replay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Flat page bar: a bare back arrow, then the title over a one-line subtitle, then the page's own [trailing]
 * action. The bar itself is not boxed; a [trailing] pill belongs to the page.
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

/**
 * The "Errors" switch of a replay page: a labelled pill, lit while rule violations show on the boards. A text label
 * rather than an icon alone, so the switch reads without knowing the glyph. Off, the pill has a thin outline so it
 * reads against the page, and its ripple is clipped to the pill.
 */
@Composable
internal fun ShowErrorsToggle(showErrors: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = PillShape,
        color = if (showErrors) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (showErrors) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (showErrors) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .heightIn(min = ReplaySizes.TouchTarget)
            .testTag(ReplayTags.SHOW_ERRORS)
            .clip(PillShape)
            .toggleable(value = showErrors, role = Role.Switch, onValueChange = onChange)
            .semantics {
                contentDescription = ReplayCopy.SHOW_ERRORS
                stateDescription = if (showErrors) ReplayCopy.ON else ReplayCopy.OFF
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(ReplayIcons.Errors, contentDescription = null, modifier = Modifier.size(ReplaySizes.Icon))
            Text(ReplayCopy.ERRORS, style = MaterialTheme.typography.labelLarge)
        }
    }
}
