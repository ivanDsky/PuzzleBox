package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import com.ivandsky.puzzlebox.gameui.CardSlot
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val Weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
private const val DISABLED_ALPHA = 0.3f
private val Swatch = 12.dp

/** The disc for a phone's ([compact]) or a tablet's layout; [archiveCellFor] sets the row around it. */
internal fun archiveDiscFor(compact: Boolean): Dp = if (compact) 42.dp else 46.dp

/**
 * A game's month calendar (06-a §Month calendar): ‹ [month] › with the archive numbers in
 * it, the "#1" / year / "Today" jumps, Monday-first weekdays, six weeks of [ArchiveDayDisc]s
 * from [days] (the month's archive days) and the legend. ‹ › and the "#1" jump call [onMonth]
 * inside [range] (#1 .. today); the "Today" jump calls [onToday]; a disc calls [onPick]. The ring
 * marks [picked], else today.
 * A non-null [onRandom] puts a Random chip in [accent]'s container at the jump row's right edge.
 * [compact] is the phone's smaller disc and row. [month] and [days] are always one loaded month: while a month the
 * user asked for ([requested], the one ‹ › and the jumps step from) is still loading, they stay the last loaded one, and
 * when it lands the title, numbers and grid change together (the crossfade of [ArchiveMonthGrid]).
 * With [swipe] (backlog #1668) the grid is a pager ([ArchiveMonthPager]) over [around], the months loaded beside [month]:
 * a swipe asks for the month it lands on through [onMonth], ‹ › and the jumps slide to theirs, and the title and numbers
 * always belong to the month the pager is showing.
 */
@Composable
internal fun ArchiveMonthCalendar(
    range: ClosedRange<LocalDate>,
    month: YearMonth,
    days: List<ArchiveDay>,
    picked: LocalDate?,
    offline: Boolean,
    accent: GameAccent,
    compact: Boolean,
    onMonth: (YearMonth) -> Unit,
    onToday: () -> Unit,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onRandom: (() -> Unit)? = null,
    requested: YearMonth = month,
    around: Map<YearMonth, List<ArchiveDay>> = emptyMap(),
    swipe: Boolean = LocalArchiveMonthSwipe.current,
) {
    val nav = remember(range) { ArchiveMonthNav(range) }
    val reduced = rememberReducedMotion()
    val style = ArchiveMonthStyle(nav, picked, range.endInclusive, offline, accent, compact, reduced)
    val pager = if (swipe) rememberArchiveMonthPager(nav, ArchiveMonthSource(month, days, around), requested, reduced, onMonth) else null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = Spacing.lg)
            .testTag("library.calendar"),
    ) {
        Header(nav, pager?.month ?: month, requested, pager?.days ?: days, onMonth)
        ArchiveMonthJumps(nav, requested, accent, reduced, onMonth, onToday, onRandom)
        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Weekdays.forEach { letter ->
                Text(
                    letter,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (pager != null) ArchiveMonthPager(style, pager, onPick) else ArchiveMonthGrid(style, month, days, onPick)
        Spacer(Modifier.weight(1f, fill = false).height(10.dp))
        Legend(accent.accent, offline)
    }
}

@Composable
private fun Header(
    nav: ArchiveMonthNav,
    month: YearMonth,
    requested: YearMonth,
    days: List<ArchiveDay>,
    onMonth: (YearMonth) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(nav.hasPrevious(requested), "library.calendar.prev", flip = true) { onMonth(nav.previous(requested)) }
        Column(
            Modifier.weight(1f).testTag("library.calendar.month.$month"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardSlot(month, 26.dp) { shown ->
                Text(
                    shown.format(MonthTitle),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val numbers = if (days.isEmpty()) "" else numberSpan(days.first().number, days.last().number)
            CardSlot(numbers, 16.dp) { shown ->
                Text(
                    shown,
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        StepButton(nav.hasNext(requested), "library.calendar.next", flip = false) { onMonth(nav.next(requested)) }
    }
}

/** "#567 – #598", or one number for a one-day month. */
private fun numberSpan(lo: Int, hi: Int): String = if (lo == hi) "#${count(lo)}" else "#${count(lo)} – #${count(hi)}"

@Composable
private fun StepButton(enabled: Boolean, tag: String, flip: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).alpha(if (enabled) 1f else DISABLED_ALPHA).testTag(tag),
    ) {
        Icon(
            PuzzleIcons.Chevron,
            contentDescription = if (flip) "Previous month" else "Next month",
            modifier = if (flip) Modifier.rotate(180f) else Modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(accent: Color, offline: Boolean) {
    val neutral = MaterialTheme.colorScheme.onSurface
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        listOf(
            DiscKind.Solved to "Solved",
            DiscKind.InProgress to "In progress",
            DiscKind.Saved to "Saved on this device",
            DiscKind.NotOpened to if (offline) "Needs a connection" else "Not opened yet",
        ).forEach { (kind, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Swatch).drawBehind { drawDisc(kind, accent, neutral, 1.5.dp, 1.2.dp) })
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
