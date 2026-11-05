package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val DayBox = 32.dp
private val TodayRing = 2.dp

/**
 * A week of [days] as seven columns, oldest to newest (spec A18): the weekday initial,
 * the day number and that day's [DayDots] out of [total] games. Today's number is
 * ringed in ink, and any day with a solve shows its number in the success text colour.
 * Callers build the [WeekDay]s; the strip only draws them.
 */
@Composable
fun WeekStrip(days: List<WeekDay>, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        days.forEach { day ->
            WeekStripDay(day, total, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekStripDay(day: WeekDay, total: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = weekdayInitial(day.date, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(DayBox)
                .then(if (day.isToday) Modifier.border(TodayRing, colors.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = if (day.solved.isEmpty()) colors.onSurface else LocalPrimaryText.current,
            )
        }
        DayDots(solved = day.solved, total = total)
    }
}

/** The weekday's narrow name in caps for the Eyebrow kicker ("M" for Monday in English). */
internal fun weekdayInitial(date: LocalDate, locale: Locale): String =
    date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)

// Preview: Monday to Sunday with today on Wednesday; earlier days mix solves, later
// days are still empty.
@PuzzleBoxPreviews
@Composable
internal fun WeekStripPreview() {
    val zip = GameBrand.Neutral.copy(accent = AccentSlot.Blue)
    val tango = GameBrand.Neutral.copy(accent = AccentSlot.Orange)
    val queens = GameBrand.Neutral.copy(accent = AccentSlot.Violet)
    val monday = LocalDate.of(2026, 11, 2)
    val solves = listOf(listOf(zip, tango, queens), listOf(tango), listOf(zip, queens))
    val days = (0 until 7).map { offset ->
        WeekDay(
            date = monday.plusDays(offset.toLong()),
            solved = solves.getOrElse(offset) { emptyList() },
            isToday = offset == 2,
        )
    }
    PuzzleBoxTheme {
        WeekStrip(
            days = days,
            total = 3,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(Spacing.md),
        )
    }
}
