package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Settings Haptics block: one Vibration switch for every haptic the app plays, whatever a game's own feel says.
 * Stateless: it draws [enabled] and reports the new value of the tapped row.
 */
@Composable
internal fun VibrationSection(enabled: Boolean, onSet: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("settings.vibration"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionTitle("Haptics")
        SettingsSwitchRow("Vibration", enabled, "settings.vibration.switch", onSet)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun VibrationSectionPreview() {
    PuzzleBoxTheme { VibrationSection(enabled = true, onSet = {}) }
}
