package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Settings Feedback block: one Vibration switch for every haptic the app plays, whatever a game's own feel says.
 * Stateless: it draws [enabled] and reports the new value of the tapped row.
 */
@Composable
internal fun VibrationSection(enabled: Boolean, onSet: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("settings.vibration"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionTitle("Feedback")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinRowHeight)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onSet)
                .testTag("settings.vibration.switch"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Vibration", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

private val MinRowHeight = 48.dp

@PuzzleBoxPreviews
@Composable
internal fun VibrationSectionPreview() {
    PuzzleBoxTheme { VibrationSection(enabled = true, onSet = {}) }
}
