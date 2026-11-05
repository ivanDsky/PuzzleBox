package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.DayDots
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val DayCircle = 32.dp
private val TodayRing = 2.dp
private val DotsGap = 2.dp
private val FilterIcon = 16.dp
private const val DIMMED = 0.38f

/** The Monday that starts [day]'s week. */
internal fun weekOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** [month] as whole Monday-to-Sunday weeks; the first and last week borrow the neighbours' days. */
internal fun monthGrid(month: YearMonth): List<List<LocalDate>> {
    val start = weekOf(month.atDay(1))
    val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { it <= end }
        .chunked(DayOfWeek.entries.size)
        .toList()
}

/** The weekday's narrow name in caps: "M" for Monday in English. */
private fun weekdayLetter(weekday: DayOfWeek, locale: Locale): String =
    weekday.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)

/** The collapsed calendar: the seven days from [monday], each under its weekday letter. */
@Composable
internal fun CalendarWeek(monday: LocalDate, state: HistoryUiState, onSelectDay: (LocalDate?) -> Unit) {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth().testTag("history.calendar.week")) {
        (0L until DayOfWeek.entries.size).map(monday::plusDays).forEach { date ->
            CalendarDay(
                date = date,
                weekday = weekdayLetter(date.dayOfWeek, locale),
                inMonth = true,
                state = state,
                onSelectDay = onSelectDay,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The expanded calendar: one weekday row, then [month]'s weeks. */
@Composable
internal fun CalendarMonth(month: YearMonth, state: HistoryUiState, onSelectDay: (LocalDate?) -> Unit) {
    val locale = Locale.getDefault()
    Column(Modifier.fillMaxWidth().testTag("history.calendar.grid")) {
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
            DayOfWeek.entries.forEach { weekday ->
                Text(
                    text = weekdayLetter(weekday, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        monthGrid(month).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    CalendarDay(
                        date = date,
                        weekday = null,
                        inMonth = YearMonth.from(date) == month,
                        state = state,
                        onSelectDay = onSelectDay,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One day: its [weekday] letter (week view only) over its number — ringed today,
 * ink-filled when selected — over its dots. Later days and the neighbouring months'
 * days are dimmed with no dots; only a day the tab has a row on taps.
 */
@Composable
private fun CalendarDay(
    date: LocalDate,
    weekday: String?,
    inMonth: Boolean,
    state: HistoryUiState,
    onSelectDay: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val today = state.today
    val later = today == null || date > today
    val selected = date == state.selectedDay
    val circle = when {
        selected -> Modifier.background(colors.onSurface, CircleShape)
        date == today -> Modifier.border(TodayRing, colors.onSurface, CircleShape)
        else -> Modifier
    }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = inMonth && !later && date in state.daysWithRows) { onSelectDay(date) }
            .then(if (inMonth) Modifier.testTag("history.calendar.day.$date") else Modifier)
            .alpha(if (!inMonth || later) DIMMED else 1f)
            .padding(bottom = DotsGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DotsGap),
    ) {
        weekday?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Box(modifier = Modifier.size(DayCircle).then(circle), contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = if (selected) colors.surface else colors.onSurface,
            )
        }
        // Neighbouring and later days keep the dots' height, so every week row lines up.
        DayDots(
            solved = state.solvedByDay[date].orEmpty(),
            total = state.dotsPerDay,
            modifier = Modifier.alpha(if (inMonth && !later) 1f else 0f),
        )
    }
}

/** The list's day filter: the day's name and a ✕; tapping it shows every day again. */
@Composable
internal fun DayFilterPill(day: LocalDate, today: LocalDate?, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClear,
        modifier = modifier.testTag("history.day_filter"),
        shape = PillShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = dayGroupLabel(day, today), style = MaterialTheme.typography.labelLarge)
            Icon(imageVector = PuzzleIcons.Close, contentDescription = "Show all days", modifier = Modifier.size(FilterIcon))
        }
    }
}
