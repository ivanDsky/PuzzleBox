package com.ivandsky.puzzlebox.profile.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.DialogCard
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import java.time.LocalTime

private const val TITLE = "Reminder time"

/**
 * The reminder time picker: a centered dialog with the dial, 12-hour or 24-hour as the device is set. Set ([onSet]) hands back the
 * picked hour and minute; Cancel, the scrim and Back ([onDismiss]) change nothing. The caller closes it after either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(initial: LocalTime, onSet: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AppDialog(
        title = TITLE,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("settings.reminders.dialog"),
        content = { DialogDial(state) },
        actions = { DialogActions(onSet = { onSet(LocalTime.of(state.hour, state.minute)) }, onDismiss = onDismiss) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogDial(state: TimePickerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TimePicker(state) }
}

@Composable
private fun DialogActions(onSet: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTextButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.testTag("settings.reminders.dialog.cancel"))
        PrimaryButton(text = "Set", onClick = onSet, modifier = Modifier.testTag("settings.reminders.dialog.set"))
    }
}

// The Studio canvas does not render AppDialog's platform window, so the preview composes the card it hosts.
@OptIn(ExperimentalMaterial3Api::class)
@PuzzleBoxPreviews
@Composable
internal fun ReminderTimeDialogPreview() {
    PuzzleBoxTheme {
        DialogCard(Modifier.padding(horizontal = Spacing.xxl).widthIn(max = 380.dp).fillMaxWidth()) {
            DialogDial(rememberTimePickerState(initialHour = 19, initialMinute = 0, is24Hour = false))
            DialogActions(onSet = {}, onDismiss = {})
        }
    }
}
