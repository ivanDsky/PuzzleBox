package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.gameui.CardSlot
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val Weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
private const val DISABLED_ALPHA = 0.3f
private const val WEEK = 7
private val ChipGap = 6.dp
private val Swatch = 12.dp

/** The disc for a phone's ([compact]) or a tablet's layout; [archiveCellFor] sets the row around it. */
internal fun archiveDiscFor(compact: Boolean): Dp = if (compact) 42.dp else 46.dp

/**
 * A game's month calendar (06-a §Month calendar): ‹ [month] › with the archive numbers in
 * it, the "#1" / year / "Today" jumps, Monday-first weekdays, six weeks of [ArchiveDayDisc]s
 * from [days] (the month's archive days) and the legend. ‹ › and the jumps call [onMonth]
 * inside [range] (#1 .. today); a disc calls [onPick]. The ring marks [picked], else today.
 * [compact] is the phone's smaller disc and row. A month change fades the grid in.
 */
@Composable
internal fun ArchiveMonthCalendar(
    range: ClosedRange<LocalDate>,
    month: YearMonth,
    days: List<ArchiveDay>,
    picked: LocalDate?,
    offline: Boolean,
    accent: Color,
    compact: Boolean,
    onMonth: (YearMonth) -> Unit,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = remember(range) { ArchiveMonthNav(range) }
    val reduced = rememberReducedMotion()
    val today = range.endInclusive
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = Spacing.lg)
            .testTag("library.calendar"),
    ) {
        Header(nav, month, days, onMonth)
        Jumps(nav, month, reduced, onMonth)
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
        val byDate = remember(days) { days.associateBy { it.date } }
        val disc = archiveDiscFor(compact)
        val cell = archiveCellFor(disc)
        key(month) {
            EnterFade(0, reduced, bleed = SelectedRingRoom) {
                Column {
                    nav.grid(month).chunked(WEEK).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                val inMonth = YearMonth.from(date) == month
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    ArchiveDayDisc(
                                        date = date,
                                        day = byDate[date]?.takeIf { inMonth && nav.contains(date) },
                                        today = date == today,
                                        selected = inMonth && date == (picked ?: today),
                                        offline = offline,
                                        accent = accent,
                                        cell = cell,
                                        disc = disc,
                                        reduced = reduced,
                                        onClick = { onPick(date) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f, fill = false).height(10.dp))
        Legend(accent, offline)
    }
}

@Composable
private fun Header(nav: ArchiveMonthNav, month: YearMonth, days: List<ArchiveDay>, onMonth: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(nav.hasPrevious(month), "library.calendar.prev", flip = true) { onMonth(nav.previous(month)) }
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
        StepButton(nav.hasNext(month), "library.calendar.next", flip = false) { onMonth(nav.next(month)) }
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
private fun Jumps(nav: ArchiveMonthNav, month: YearMonth, reduced: Boolean, onMonth: (YearMonth) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = ChipGap, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
        verticalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        val jumps = nav.jumps(month)
        jumps.forEachIndexed { index, jump ->
            val name = when (index) {
                0 -> "first"
                jumps.lastIndex -> "today"
                else -> jump.label
            }
            JumpChip(jump, "library.calendar.jump.$name", reduced) { onMonth(jump.month) }
        }
    }
}

@Composable
private fun JumpChip(jump: MonthJump, tag: String, reduced: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressScale(interaction, reduced)
            .height(36.dp)
            .clip(PillShape)
            .background(if (jump.selected) scheme.onSurface else scheme.surfaceContainerHighest)
            .selectable(selected = jump.selected, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            jump.label,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = if (jump.selected) scheme.surface else scheme.onSurface,
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
