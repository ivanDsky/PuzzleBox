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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.DayDots
import com.ivandsky.puzzlebox.gameui.GameBrand
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val DayCircle = 32.dp
private val TodayRing = 2.dp
private val FilterIcon = 16.dp
private const val DIMMED = 0.38f
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/** [month] as whole Monday-to-Sunday weeks; the first and last week borrow the neighbours' days. */
internal fun monthGrid(month: YearMonth): List<List<LocalDate>> {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { it <= end }
        .chunked(DayOfWeek.entries.size)
        .toList()
}

/**
 * History's month calendar (spec A18) in one card: "November 2026" with arrows back to
 * the oldest month with history and forward to the current one (swiping pages too), a
 * Monday-first weekday row, and each day's number over its [DayDots]. Today is ringed;
 * the selected day fills with ink. A day with any row can be tapped to filter the list
 * ([onSelectDay]; the selected day again clears it). Later days and the neighbouring
 * months' days are dimmed and never tap. Shows nothing until the state has a month.
 */
@Composable
internal fun HistoryCalendar(
    state: HistoryUiState,
    onShowMonth: (YearMonth) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    val month = state.month ?: return
    val today = state.today ?: return
    val earliest = state.earliestMonth ?: month
    val current = YearMonth.from(today)
    OutlinedCard(
        modifier = modifier.fillMaxWidth().testTag("history.calendar"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CalendarHeader(
                month = month,
                canGoBack = month > earliest,
                canGoForward = month < current,
                onShowMonth = onShowMonth,
            )
            WeekdayRow()
            MonthPager(
                month = month,
                earliest = earliest,
                current = current,
                onShowMonth = onShowMonth,
            ) { shown ->
                MonthGrid(shown, today, state, onSelectDay)
            }
        }
    }
}

/** The month name and its arrows; the back arrow is the forward chevron turned around. */
@Composable
private fun CalendarHeader(
    month: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onShowMonth: (YearMonth) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = month.format(MONTH_FORMAT),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm)
                .testTag("history.calendar.month"),
        )
        IconButton(
            onClick = { onShowMonth(month.minusMonths(1)) },
            enabled = canGoBack,
            modifier = Modifier.testTag("history.calendar.prev"),
        ) {
            Icon(imageVector = PuzzleIcons.Chevron, contentDescription = "Last month", modifier = Modifier.rotate(180f))
        }
        IconButton(
            onClick = { onShowMonth(month.plusMonths(1)) },
            enabled = canGoForward,
            modifier = Modifier.testTag("history.calendar.next"),
        ) {
            Icon(imageVector = PuzzleIcons.Chevron, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayRow() {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { weekday ->
            Text(
                text = weekday.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Swipeable months, newest at page 0 and older to its left (reverse layout), so a newly
 * loaded older month adds a page without moving the shown one. The arrows drive it
 * through [month]; a settled swipe reports its month through [onShowMonth].
 */
@Composable
private fun MonthPager(
    month: YearMonth,
    earliest: YearMonth,
    current: YearMonth,
    onShowMonth: (YearMonth) -> Unit,
    page: @Composable (YearMonth) -> Unit,
) {
    fun pageOf(shown: YearMonth) = ChronoUnit.MONTHS.between(shown, current).toInt()
    val pagerState = rememberPagerState(initialPage = pageOf(month)) { pageOf(earliest) + 1 }
    val reducedMotion = rememberReducedMotion()
    val report by rememberUpdatedState(onShowMonth)
    LaunchedEffect(month) {
        val target = pageOf(month)
        if (pagerState.currentPage != target) {
            if (reducedMotion) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState, current) {
        snapshotFlow { pagerState.settledPage }.collect { report(current.minusMonths(it.toLong())) }
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = true,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) { index ->
        page(current.minusMonths(index.toLong()))
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, state: HistoryUiState, onSelectDay: (LocalDate?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        monthGrid(month).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == month
                    CalendarDay(
                        date = date,
                        inMonth = inMonth,
                        isToday = date == today,
                        selected = date == state.selectedDay,
                        dimmed = !inMonth || date > today,
                        tappable = inMonth && date <= today && date in state.daysWithRows,
                        solved = state.solvedByDay[date].orEmpty(),
                        total = state.dotsPerDay,
                        onClick = { onSelectDay(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One day: its number (ringed today, ink-filled when selected) over its dots. */
@Composable
private fun CalendarDay(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    selected: Boolean,
    dimmed: Boolean,
    tappable: Boolean,
    solved: List<GameBrand>,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val circle = when {
        selected -> Modifier.background(colors.onSurface, CircleShape)
        isToday -> Modifier.border(TodayRing, colors.onSurface, CircleShape)
        else -> Modifier
    }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = tappable, onClick = onClick)
            .then(if (inMonth) Modifier.testTag("history.calendar.day.$date") else Modifier)
            .alpha(if (dimmed) DIMMED else 1f)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(modifier = Modifier.size(DayCircle).then(circle), contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = if (selected) colors.surface else colors.onSurface,
            )
        }
        // Neighbouring and later days keep the dots' height, so every week row lines up.
        DayDots(solved = solved, total = total, modifier = Modifier.alpha(if (inMonth && !dimmed) 1f else 0f))
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
