package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Public or Link only choice (nav-rethink C1): two flat chips, each at least 48dp tall and wide, in one row at every
 * width. The chips turn off while not [enabled] (a publish runs) or when the [choice] is locked.
 */
@Composable
internal fun PublishVisibilityPicker(
    choice: PublishVisibility.Choice,
    onPick: (Visibility) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.selectableGroup().testTag("create.details.visibility"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VisibilityChip(Visibility.Public, "Public", "public", choice, onPick, enabled)
        VisibilityChip(Visibility.LinkOnly, "Link only", "link", choice, onPick, enabled)
    }
}

@Composable
private fun VisibilityChip(
    which: Visibility,
    label: String,
    tag: String,
    choice: PublishVisibility.Choice,
    onPick: (Visibility) -> Unit,
    enabled: Boolean,
) {
    FilterChip(
        selected = choice.pick == which,
        onClick = { onPick(which) },
        enabled = enabled && !choice.locked,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = TargetSize).widthIn(min = TargetSize).testTag("create.details.visibility.$tag"),
    )
}

private val TargetSize = 48.dp

@PuzzleBoxPreviews
@Composable
private fun PublishVisibilityPickerPreview() {
    PuzzleBoxTheme { PublishVisibilityPicker(PublishVisibility.Choice(Visibility.Public), onPick = {}) }
}

@PuzzleBoxPreviews
@Composable
private fun PublishVisibilityPickerLinkOnlyPreview() {
    PuzzleBoxTheme { PublishVisibilityPicker(PublishVisibility.Choice(Visibility.LinkOnly), onPick = {}) }
}
