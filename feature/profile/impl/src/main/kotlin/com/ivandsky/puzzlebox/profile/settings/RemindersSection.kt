package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import java.time.LocalTime

/**
 * The Settings Reminders block: the Daily reminder switch and, when notifications are blocked, the line that says so
 * with a button to the system settings. Stateless. [onSetTime] is for the Time row, which comes later.
 */
@Composable
internal fun RemindersSection(
    state: RemindersState,
    onSetEnabled: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSetTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("settings.reminders"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionTitle("Reminders")
        SettingsSwitchRow(
            label = "Daily reminder",
            checked = state.on,
            tag = "settings.reminders.switch",
            onCheckedChange = onSetEnabled,
            support = "Get a reminder to play today's puzzles.",
        )
        if (state.blocked) {
            Text(
                text = "Notifications are blocked for PuzzleBox. Turn them on in system settings to get a reminder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings.reminders.blocked"),
            )
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = 48.dp).testTag("settings.reminders.opensettings"),
            ) { Text("Open settings") }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun RemindersSectionPreview() {
    PuzzleBoxTheme {
        RemindersSection(
            state = RemindersState(on = false, time = LocalTime.of(19, 0), blocked = true, askPermission = false),
            onSetEnabled = {},
            onOpenSettings = {},
            onSetTime = {},
        )
    }
}
