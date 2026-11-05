package com.ivandsky.puzzlebox.play

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A game's "Past puzzles" (A22): the M3 date picker over [range] only — days outside it
 * are disabled, the year menu spans the range's years, and it opens on the range's end
 * (the game's today). Open reports the picked day to [onOpen]; picking fetches nothing,
 * the day loads when its route opens. Cancel or a tap outside is [onDismiss]. The dialog's
 * enter/exit is the platform window's own, so no motion here needs a reduced-motion path.
 */
@Composable
internal fun PastPuzzlesPicker(
    range: ClosedRange<LocalDate>,
    onOpen: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberPastPuzzlesState(range)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppTextButton(
                text = "Open",
                onClick = { state.selectedDateMillis?.let { onOpen(it.toUtcDay()) } },
                enabled = state.selectedDateMillis != null,
            )
        },
        dismissButton = { AppTextButton(text = "Cancel", onClick = onDismiss) },
    ) {
        DatePicker(state = state)
    }
}

/** M3 dates are UTC midnights; [range] is in the game's calendar days, so compare as UTC days. */
@Composable
private fun rememberPastPuzzlesState(range: ClosedRange<LocalDate>): DatePickerState {
    val selectable = remember(range) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toUtcDay() in range

            override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
        }
    }
    return rememberDatePickerState(
        initialSelectedDateMillis = range.endInclusive.toUtcMillis(),
        yearRange = range.start.year..range.endInclusive.year,
        selectableDates = selectable,
    )
}

private fun Long.toUtcDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

// The platform dialog window does not render on the Studio canvas (see designsystem's
// Dialogs.kt), so the preview composes the picker the dialog hosts.
@PuzzleBoxPreviews
@Composable
private fun PastPuzzlesPickerPreview() {
    PuzzleBoxTheme {
        DatePicker(state = rememberPastPuzzlesState(LocalDate.of(2026, 10, 20)..LocalDate.of(2026, 11, 4)))
    }
}
