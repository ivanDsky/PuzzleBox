package com.ivandsky.puzzlebox.profile.settings

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Settings Reminders block: the Daily reminder switch and, when notifications are blocked, the line that says so
 * with a button to the system settings. While the reminder is on, a Time row shows the stored time and opens the picker; Set
 * reports it through [onSetTime]. Only the open picker is local state.
 */
@Composable
internal fun RemindersSection(
    state: RemindersState,
    onSetEnabled: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSetTime: (LocalTime) -> Unit,
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
        if (state.on) {
            var picking by rememberSaveable { mutableStateOf(false) }
            TimeRow(state.time, onClick = { picking = true })
            if (picking) {
                ReminderTimeDialog(
                    initial = state.time,
                    onSet = {
                        picking = false
                        onSetTime(it)
                    },
                    onDismiss = { picking = false },
                )
            }
        }
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

/** The same row shape as the switch row: the whole row taps, 48 dp at least; the time in the device's short format, 12 or 24 hour. */
@Composable
private fun TimeRow(time: LocalTime, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("settings.reminders.time"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "Time", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = formatReminderTime(time, DateFormat.is24HourFormat(context)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatReminderTime(time: LocalTime, is24Hour: Boolean): String {
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), if (is24Hour) "Hm" else "hm")
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(time)
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
