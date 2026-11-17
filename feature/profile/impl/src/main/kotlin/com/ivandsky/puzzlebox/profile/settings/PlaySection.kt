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
 * The Settings Play block: the switch for the time left to beat during a duel against yourself.
 * Stateless: it draws [timeLeft] and reports the new value of the tapped row.
 */
@Composable
internal fun PlaySection(timeLeft: Boolean, onSetTimeLeft: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("settings.play"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionTitle("Play")
        SettingsSwitchRow(
            label = "Show time left when you duel yourself",
            checked = timeLeft,
            tag = "settings.play.timeleft",
            onCheckedChange = onSetTimeLeft,
            support = "Counts down to your first solve's time during a duel against yourself.",
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun PlaySectionPreview() {
    PuzzleBoxTheme { PlaySection(timeLeft = true, onSetTimeLeft = {}) }
}
