package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.DayRing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Inside DayRing's 17dp inner edge, so a 2dp sliver separates the disc from the ring.
private val DayDisc = 30.dp
private val WeekdayGap = Spacing.xs
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
 * One day: its [weekday] letter (week view only) over its [DayRing], with the number
 * inside — on an ink disc when selected, bold on a tinted disc today. Later days and the
 * neighbouring months' days are dimmed with no ring; a day taps when the tab can open it
 * ([HistoryUiState.canSelect]: a row on it, or on a game's tab any archive day).
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
    val disc = when {
        selected -> colors.onSurface
        date == today -> colors.surfaceContainerHighest
        else -> null
    }
    val shown = inMonth && !later
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = inMonth && state.canSelect(date)) { onSelectDay(date) }
            .then(if (inMonth) Modifier.testTag("history.calendar.day.$date") else Modifier)
            .alpha(if (shown) 1f else DIMMED),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WeekdayGap),
    ) {
        weekday?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        // No ring is total 0: the ring keeps its size, so every week row lines up.
        DayRing(
            solved = if (shown) state.solvedByDay[date].orEmpty() else emptyList(),
            total = if (shown) state.gamesPerDay else 0,
        ) {
            disc?.let { Box(Modifier.size(DayDisc).background(it, CircleShape)) }
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.tabular(),
                fontWeight = if (date == today && !selected) FontWeight.Bold else null,
                color = if (selected) colors.surface else colors.onSurface,
            )
        }
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
