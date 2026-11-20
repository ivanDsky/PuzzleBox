package com.ivandsky.puzzlebox.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.ChoiceRows
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SegmentedToggle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.ThemeMode

private fun label(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun description(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follows your phone's setting"
    ThemeMode.LIGHT -> "Always light"
    ThemeMode.DARK -> "Always dark"
}

private fun tag(mode: ThemeMode): String = "profile.appearance.${mode.name.lowercase()}"

/**
 * Appearance (N4, moved here from the Home gear): System / Light / Dark, one tap each
 * (tag `profile.appearance.<mode>`, lowercase). [onSelect] persists the choice. With [choiceRows] the
 * choice is [ChoiceRows] with a line under each mode, else the segmented control.
 */
@Composable
internal fun AppearanceRow(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    choiceRows: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("profile.appearance"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (choiceRows) {
            ChoiceRows(
                options = ThemeMode.entries,
                selected = current,
                onSelect = onSelect,
                label = ::label,
                description = ::description,
                tag = ::tag,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SegmentedToggle(
                options = ThemeMode.entries,
                selected = current,
                onSelect = onSelect,
                label = ::label,
                tag = ::tag,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun AppearanceRowChoiceRowsPreview() {
    PuzzleBoxTheme {
        AppearanceRow(current = ThemeMode.SYSTEM, onSelect = {}, choiceRows = true, modifier = Modifier.padding(Spacing.xl))
    }
}
