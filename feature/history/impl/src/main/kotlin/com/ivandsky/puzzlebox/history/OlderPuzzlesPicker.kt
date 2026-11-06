package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.PastDay
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.gameui.pastDayHeadline
import com.ivandsky.puzzlebox.gameui.pastDayStatusLine
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * "Older puzzles" for one game (A12, A22): the M3 date picker over [range] only — days
 * outside it are disabled, the year menu spans the range's years, and it opens on
 * yesterday ([olderPuzzlesInitialDay]: the range's end is the game's today). Its headline
 * (`history.older.headline`, A20/A24) reads the selected day as "<Game> #N · <status>"
 * from [pastDay] — local data only. Open reports the picked day to [onOpen]; picking
 * fetches nothing, the day loads when its slot opens. Cancel or a tap outside is
 * [onDismiss]. The dialog's enter/exit is the platform window's own.
 */
@Composable
internal fun OlderPuzzlesPicker(
    range: ClosedRange<LocalDate>,
    gameName: String,
    pastDay: (LocalDate) -> Flow<PastDay>,
    onOpen: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberOlderPuzzlesState(range)
    val selected = state.selectedDateMillis?.toUtcDay()
    val shown by remember(selected) { selected?.let(pastDay) ?: emptyFlow() }.collectAsState(initial = null)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppTextButton(
                text = "Open",
                onClick = { selected?.let(onOpen) },
                enabled = selected != null,
                modifier = Modifier.testTag("history.older.open"),
            )
        },
        dismissButton = { AppTextButton(text = "Cancel", onClick = onDismiss) },
    ) {
        DatePicker(
            state = state,
            headline = { OlderPuzzlesHeadline(gameName, shown) },
            modifier = Modifier.testTag("history.older.picker"),
        )
    }
}

/** Yesterday in [range]'s calendar (its end is the game's today), clamped into [range]. */
internal fun olderPuzzlesInitialDay(range: ClosedRange<LocalDate>): LocalDate =
    range.endInclusive.minusDays(1).coerceIn(range.start, range.endInclusive)

/** "<Game> #N · <status>" for [day]; just the game's name until the day is read. */
@Composable
private fun OlderPuzzlesHeadline(gameName: String, day: PastDay?) {
    Text(
        text = if (day == null) gameName else "${pastDayHeadline(gameName, day.number)} · ${pastDayStatusLine(day.status)}",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(start = Spacing.xl, end = Spacing.md, bottom = Spacing.md)
            .testTag("history.older.headline"),
    )
}

/** M3 dates are UTC midnights; [range] is in the game's calendar days, so compare as UTC days. */
@Composable
private fun rememberOlderPuzzlesState(range: ClosedRange<LocalDate>): DatePickerState {
    val selectable = remember(range) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toUtcDay() in range

            override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
        }
    }
    return rememberDatePickerState(
        initialSelectedDateMillis = olderPuzzlesInitialDay(range).toUtcMillis(),
        yearRange = range.start.year..range.endInclusive.year,
        selectableDates = selectable,
    )
}

private fun Long.toUtcDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

// The platform dialog window does not render on the Studio canvas (see designsystem's
// Dialogs.kt), so the previews compose the picker the dialog hosts, one per headline state.
private val previewRange = LocalDate.of(2026, 10, 20)..LocalDate.of(2026, 11, 4)
private val previewDay = PastDay(GameId("zip"), LocalDate.of(2026, 11, 3), 592, PastDayStatus.Solved(95_000L))

@Composable
private fun OlderPuzzlesPickerPreviewBody(day: PastDay?) {
    PuzzleBoxTheme {
        DatePicker(
            state = rememberOlderPuzzlesState(previewRange),
            headline = { OlderPuzzlesHeadline("Zip", day) },
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun OlderPuzzlesPickerSolvedPreview() = OlderPuzzlesPickerPreviewBody(previewDay)

@PuzzleBoxPreviews
@Composable
private fun OlderPuzzlesPickerNeedsConnectionPreview() =
    OlderPuzzlesPickerPreviewBody(previewDay.copy(status = PastDayStatus.NeedsConnection))

@PuzzleBoxPreviews
@Composable
private fun OlderPuzzlesPickerLoadingPreview() = OlderPuzzlesPickerPreviewBody(null)
