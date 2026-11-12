package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.navLiftSource
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GameCardColors
import com.ivandsky.puzzlebox.gameui.GameCardSurface
import com.ivandsky.puzzlebox.gameui.GameMotif
import com.ivandsky.puzzlebox.gameui.PastDayMark
import com.ivandsky.puzzlebox.gameui.SkeletonLine
import com.ivandsky.puzzlebox.gameui.StatusMark
import com.ivandsky.puzzlebox.gameui.pastDayAction
import com.ivandsky.puzzlebox.gameui.pastDayStatusLine
import com.ivandsky.puzzlebox.play.api.GameKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EyebrowDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val PastEyebrowDate = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

// "Try again" reads unavailable while offline, yet stays a tap target: its tap is the retry.
private const val UNAVAILABLE_ALPHA = 0.5f
private const val TAG_WELL_ALPHA = 0.22f
private val ChipRowHeight = 48.dp

/** What the card's slots draw for one day; a change of it is what cross-fades. */
private data class DayFace(val past: ArchiveDay?, val number: Int?, val line: DayLine, val unavailable: Boolean)

/** The status slot's value: today's status (null while loading) or a past day's. */
private sealed interface DayLine {
    data class Today(val status: TodayStatus?) : DayLine
    data class Past(val status: PastDayStatus) : DayLine
}

/**
 * The game page's day card (06-a §Day card): the brand card with the big motif bottom-end,
 * the eyebrow date, "<Game> #N", the day's status with its mark, its note and its action.
 * With no [past] day it shows today ([today] is the game's day, null before it loads);
 * with one it shows that day, tagged "From the archive" or "Past puzzle", plus
 * "Back to today" ([onBackToToday]). [onPlay] opens the shown day's board. A day that
 * can't load — today offline, or a never-opened past day while [offline] — offers
 * "Try again", whose tap is [onRetry]. A day change cross-fades every line and the action
 * together in place ([DayFade]); the card's frame and size stay put. A non-null
 * [linkedIn] draws the LinkedIn sync chip in the top-end corner, beside the eyebrow, and only while the
 * card is composed.
 */
@Composable
internal fun ArchiveDayCard(
    row: LibraryRowModel,
    today: LocalDate?,
    past: ArchiveDay?,
    offline: Boolean,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onBackToToday: () -> Unit,
    modifier: Modifier = Modifier,
    linkedIn: (@Composable () -> Unit)? = null,
) {
    val card = row.card
    val colors = libraryCardColors(card.brand)
    val line = if (past == null) DayLine.Today(card.status) else DayLine.Past(past.status)
    val unavailable = when (line) {
        is DayLine.Today -> line.status == TodayStatus.Offline
        is DayLine.Past -> offline && line.status == PastDayStatus.NeedsConnection
    }
    val action = if (unavailable) onRetry else onPlay
    val fade = rememberDayFade(DayFace(past, past?.number ?: row.today, line, unavailable))
    GameCardSurface(
        colors = colors,
        onClick = action,
        // Keyed like the push onPlay makes, so the board lifts out of this card (tablet).
        modifier = modifier.fillMaxWidth().heightIn(min = 196.dp).testTag("library.day_card")
            .navLiftSource(GameKey(card.game.value, past?.date?.toString())),
        motif = {
            GameMotif(card.brand, 300.dp, 1.4f, Alignment.BottomEnd, DpOffset(46.dp, 80.dp), colors.motif, colors.motifAlpha)
        },
    ) {
        // The chip's tap target is as tall as the first row, so with it the row takes the 12dp the card would have
        // left above and below the eyebrow, and the title stays where it is without one.
        val top = if (linkedIn == null) 20.dp else 8.dp
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = top, bottom = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (linkedIn == null) 0.dp else ChipRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { fade.Slot(24.dp) { TopRow(it.past, today, colors) } }
                linkedIn?.let { slot ->
                    Spacer(Modifier.width(8.dp))
                    slot()
                }
            }
            Spacer(Modifier.weight(1f, fill = false).heightIn(min = if (linkedIn == null) 12.dp else 0.dp))
            fade.Slot(34.dp) { face ->
                Text(
                    card.name + face.number?.let { " #${count(it)}" }.orEmpty(),
                    style = MaterialTheme.typography.headlineLarge.tabular(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            fade.Slot(24.dp) { StatusRow(it.line, colors) }
            fade.Slot(20.dp) { face ->
                Text(note(face.past), style = MaterialTheme.typography.labelLarge, color = colors.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            fade.Slot(48.dp) { face -> Actions(face, colors, action, onBackToToday) }
        }
    }
}

/** The primary action, and "Back to today" while a past day is shown. */
@Composable
private fun Actions(face: DayFace, colors: GameCardColors, action: () -> Unit, onBackToToday: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = action,
            enabled = face.line != DayLine.Today(null),
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.fg, contentColor = colors.fill),
            modifier = Modifier
                .height(48.dp)
                .alpha(if (face.unavailable) UNAVAILABLE_ALPHA else 1f)
                .testTag("library.play"),
        ) {
            Text(if (face.unavailable) "Try again" else actionLabel(face.line), style = MaterialTheme.typography.labelLarge)
        }
        if (face.past != null) {
            TextButton(
                onClick = onBackToToday,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.fg),
                modifier = Modifier.height(48.dp).testTag("library.day_card.back"),
            ) {
                Text("Back to today", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The eyebrow ("TODAY · THU, NOV 5" or "WED, OCT 14, 2026"), then a past day's tag pill. */
@Composable
private fun TopRow(past: ArchiveDay?, today: LocalDate?, colors: GameCardColors) {
    val shown = past?.date ?: today
    Row(
        modifier = Modifier.then(if (shown == null) Modifier else Modifier.testTag("library.day_card.day.$shown")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val eyebrow = if (past == null) {
            "TODAY" + today?.let { " · " + it.format(EyebrowDate) }.orEmpty()
        } else {
            past.date.format(PastEyebrowDate)
        }
        Text(
            eyebrow.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fg2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (past != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                // Gives way to the LinkedIn chip beside the row when the width runs out.
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(24.dp)
                    .clip(PillShape)
                    .background(colors.fg.copy(alpha = TAG_WELL_ALPHA))
                    .padding(horizontal = 10.dp)
                    .testTag(if (past.archived) "library.day_card.tag.archived" else "library.day_card.tag.past"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (past.archived) "From the archive" else "Past puzzle",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(line: DayLine, colors: GameCardColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (line) {
            is DayLine.Today -> StatusMark(line.status, 16.dp, colors.fg)
            is DayLine.Past -> PastDayMark(line.status, 16.dp, colors.fg)
        }
        Spacer(Modifier.width(6.dp))
        val words = when (line) {
            is DayLine.Today -> line.status?.let(::dayStatusLine)
            is DayLine.Past -> pastLine(line.status)
        }
        if (words == null) {
            SkeletonLine(0.5f, 14.dp, colors.fg.copy(alpha = SKELETON_ALPHA))
        } else {
            Text(words, style = MaterialTheme.typography.bodyLarge.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Today's status on the day card: the time follows the state's words. */
internal fun dayStatusLine(status: TodayStatus): String = when (status) {
    is TodayStatus.Solved -> "Solved ${formatDuration(status.timeMs)}"
    is TodayStatus.InProgress -> "In progress · ${formatDuration(status.elapsedMs)}"
    TodayStatus.NotStarted -> "Today's puzzle"
    TodayStatus.Offline -> "Can't load it · check your connection"
}

/** A past day's status; in progress adds its saved clock, as today's line does. */
private fun pastLine(status: PastDayStatus): String = when (status) {
    is PastDayStatus.InProgress -> "In progress · ${formatDuration(status.elapsedMs)}"
    else -> pastDayStatusLine(status)
}

/** What the day means for the streak: today counts; an archive day never does (A21). */
private fun note(past: ArchiveDay?): String = when {
    past == null -> "Counts toward your streak"
    past.archived -> past.openedOn?.let { "Opened ${longDate(it)} · doesn't count toward your streak" }
        ?: "Doesn't count toward your streak"
    past.status is PastDayStatus.Solved -> "Solved on the day"
    else -> "Counts toward your best, never your streak"
}

private fun actionLabel(line: DayLine): String = when (line) {
    is DayLine.Past -> pastDayAction(line.status)
    is DayLine.Today -> when (line.status) {
        null, TodayStatus.NotStarted, TodayStatus.Offline -> "Play"
        is TodayStatus.InProgress -> "Keep going"
        is TodayStatus.Solved -> "See result"
    }
}
