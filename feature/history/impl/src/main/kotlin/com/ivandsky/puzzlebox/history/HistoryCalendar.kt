package com.ivandsky.puzzlebox.history

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val SHORT_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
private val SHORT_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

/** "November 2026" for a week inside one month, "Oct – Nov 2026" or "Dec 2026 – Jan 2027" across two. */
internal fun weekTitle(monday: LocalDate): String {
    val first = YearMonth.from(monday)
    val last = YearMonth.from(monday.plusDays(6))
    return when {
        first == last -> first.format(MONTH_YEAR)
        first.year == last.year -> "${first.format(SHORT_MONTH)} – ${last.format(SHORT_MONTH_YEAR)}"
        else -> "${first.format(SHORT_MONTH_YEAR)} – ${last.format(SHORT_MONTH_YEAR)}"
    }
}

/**
 * The week (its Monday) the calendar folds back to from [month], first match wins: the
 * [selectedDay]'s week when it is in [month]; this week when [month] holds [today]; the
 * week of [month]'s first day with a play ([daysWithRows]); else the week of the 1st.
 */
internal fun collapsedWeek(
    month: YearMonth,
    selectedDay: LocalDate?,
    today: LocalDate,
    daysWithRows: Set<LocalDate>,
): LocalDate = weekOf(
    selectedDay?.takeIf { YearMonth.from(it) == month }
        ?: today.takeIf { YearMonth.from(it) == month }
        ?: daysWithRows.filter { YearMonth.from(it) == month }.minOrNull()
        ?: month.atDay(1),
)

/**
 * History's calendar (spec A18) in one card, one week by default so the list stays in
 * view. The header names the shown week's months (or the month) and toggles the month
 * view; its arrows — and swipes — page by week, or by month when expanded, from the
 * oldest row's week or month up to the current one. Expanding shows the shown week's
 * Monday's month; folding back follows [collapsedWeek]. The card's height eases between
 * the two (instant under reduced motion).
 *
 * Today is ringed and the selected day fills with ink. A day with any row taps to filter
 * the list ([onSelectDay]; the selected day again clears it); later days never tap.
 * [onShowCalendar] reports the day whose week or month to show, and the mode. Shows
 * nothing until the state has a calendar day.
 * [foldable] false drops the header's chevron and its tap; the calendar stays the mode it is given.
 * [foldInPlace] keeps one pager across a fold so the card eases around it (the Ranks hub, behind its flag); false
 * builds a new pager per mode, as the shipped History screen does.
 */
@Composable
internal fun HistoryCalendar(
    state: CalendarState,
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    cardColor: Color,
    foldable: Boolean = true,
    foldInPlace: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shown = state.shownDay ?: return
    val today = state.today ?: return
    val expanded = state.expanded
    val pages = CalendarPages(expanded, today, earliest = state.earliestDay ?: shown)
    val index = pages.indexOf(shown)
    val reducedMotion = rememberReducedMotion()
    OutlinedCard(
        modifier = modifier.fillMaxWidth().testTag("history.calendar"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(
                    if (reducedMotion) snap() else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
                )
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            CalendarHeader(
                title = if (expanded) YearMonth.from(shown).format(MONTH_YEAR) else weekTitle(weekOf(shown)),
                expanded = expanded,
                foldable = foldable,
                canGoBack = index < pages.count - 1,
                canGoForward = index > 0,
                onToggle = {
                    if (expanded) {
                        onShowCalendar(collapsedWeek(YearMonth.from(shown), state.selectedDay, today, state.daysWithRows), false)
                    } else {
                        onShowCalendar(weekOf(shown), true)
                    }
                },
                onBack = { onShowCalendar(pages.dayAt(index + 1), expanded) },
                onForward = { onShowCalendar(pages.dayAt(index - 1), expanded) },
            )
            if (foldInPlace) {
                CalendarPager(pages, index, expanded, onPage = { onShowCalendar(pages.dayAt(it), expanded) }) { page ->
                    CalendarPage(pages.dayAt(page), expanded, state, onSelectDay)
                }
            } else {
                // A new pager per mode: its pages are weeks or months.
                key(expanded) {
                    CalendarPager(pages, index, expanded, onPage = { onShowCalendar(pages.dayAt(it), expanded) }) { page ->
                        CalendarPage(pages.dayAt(page), expanded, state, onSelectDay)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPage(day: LocalDate, expanded: Boolean, state: CalendarState, onSelectDay: (LocalDate?) -> Unit) {
    if (expanded) {
        CalendarMonth(YearMonth.from(day), state, onSelectDay)
    } else {
        CalendarWeek(day, state, onSelectDay)
    }
}

/**
 * The calendar's pages, newest (page 0, holding [today]) first: weeks, or months when
 * [expanded], back to the one holding [earliest].
 */
private class CalendarPages(private val expanded: Boolean, private val today: LocalDate, earliest: LocalDate) {
    val count: Int = indexOf(earliest) + 1

    /** How many weeks (or months) [day] lies before today's. */
    fun indexOf(day: LocalDate): Int = if (expanded) {
        ChronoUnit.MONTHS.between(YearMonth.from(day), YearMonth.from(today)).toInt()
    } else {
        ChronoUnit.WEEKS.between(weekOf(day), weekOf(today)).toInt()
    }

    /** The first day of page [index]: a Monday, or a month's 1st when expanded. */
    fun dayAt(index: Int): LocalDate = if (expanded) {
        YearMonth.from(today).minusMonths(index.toLong()).atDay(1)
    } else {
        weekOf(today).minusWeeks(index.toLong())
    }
}

/**
 * The title, which toggles the month view when [foldable] (its chevron points down to expand, up to fold), and the
 * arrows; the back arrow is the forward chevron turned around.
 */
@Composable
private fun CalendarHeader(
    title: String,
    expanded: Boolean,
    foldable: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (foldable) Modifier.clip(PillShape).clickable(onClick = onToggle).testTag("history.calendar.expand") else Modifier)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("history.calendar.month"),
            )
            if (foldable) {
                Icon(
                    imageVector = PuzzleIcons.ExpandMore,
                    contentDescription = if (expanded) "Show one week" else "Show the month",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (expanded) Modifier.rotate(180f) else Modifier,
                )
            }
        }
        IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.testTag("history.calendar.prev")) {
            Icon(imageVector = PuzzleIcons.Chevron, contentDescription = "Back", modifier = Modifier.rotate(180f))
        }
        IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.testTag("history.calendar.next")) {
            Icon(imageVector = PuzzleIcons.Chevron, contentDescription = "Forward")
        }
    }
}

/**
 * Swipeable pages, newest at page 0 and older to its left (reverse layout), so a newly
 * loaded older page adds on without moving the shown one. The arrows drive it through
 * [index]; a settled swipe to another page reports it through [onPage].
 *
 * It is one pager for both modes, so a fold only changes what its pages hold and the card eases to the new height
 * around it. A page number means another week or month once [expanded] changes, so the pager lands on the new
 * [index] at once, before it is drawn again, instead of scrolling there through pages of the wrong kind.
 */
@Composable
private fun CalendarPager(
    pages: CalendarPages,
    index: Int,
    expanded: Boolean,
    onPage: (Int) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    val count = pages.count
    val pagerState = rememberPagerState(initialPage = index) { count }
    val reducedMotion = rememberReducedMotion()
    val shownIndex by rememberUpdatedState(index)
    val report by rememberUpdatedState(onPage)
    // The mode the pager's pages were last laid out in; not state, for nothing draws from it.
    val mode = remember { booleanArrayOf(expanded) }
    val folded = mode[0] != expanded
    SideEffect {
        if (folded) {
            mode[0] = expanded
            pagerState.requestScrollToPage(index)
        }
    }
    LaunchedEffect(index) {
        if (pagerState.currentPage != index) {
            if (reducedMotion || folded) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { if (it != shownIndex) report(it) }
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = true,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        content(page)
    }
}
