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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
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
import com.ivandsky.puzzlebox.play.api.GameKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EyebrowDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val PastEyebrowDates = listOf("EEE, MMM d, yyyy", "MMM d, yyyy", "MMM d").map { DateTimeFormatter.ofPattern(it, Locale.US) }

// "Try again" reads unavailable while offline, yet stays a tap target: its tap is the retry.
private const val UNAVAILABLE_ALPHA = 0.5f
private const val TAG_WELL_ALPHA = 0.22f
private val ChipRowHeight = 48.dp

/** What the eyebrow line draws: the shown day, and the tag pill a past day carries. */
private data class TopFace(val date: LocalDate?, val past: Boolean, val archived: Boolean)

/** The status line's lead: the mark and its words. The mark's kind and the words decide whether it is the same layer. */
private data class StatusLead(val line: DayLine, val words: String?)

/**
 * The game page's day card (06-a §Day card): the brand card with the big motif bottom-end,
 * the eyebrow date, "<Game> #N", the day's status with its mark, its note and its action.
 * With no [past] day it shows today ([today] is the game's day, null before it loads);
 * with one it shows that day, tagged "From the archive" or "Past puzzle", plus
 * "Back to today" ([onBackToToday]). [onPlay] opens the shown day's board. A day that
 * can't load — today offline, or a never-opened past day while [offline] — offers
 * "Try again", whose tap is [onRetry]. A day change morphs only the text that differs ([MorphCell]): "Zip #", a
 * solved mark and the action's button stay put while the number, the time and the label blur across. The
 * card's frame and size stay put. A non-null [linkedIn] draws the LinkedIn sync chip in the top-end corner, beside
 * the eyebrow, and only while the card is composed.
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
    val number = past?.number ?: row.today
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
                Line(24.dp, Modifier.weight(1f)) {
                    MorphCell(TopFace(past?.date ?: today, past != null, past?.archived == true)) { TopRow(it, colors) }
                }
                linkedIn?.let { slot ->
                    Spacer(Modifier.width(8.dp))
                    slot()
                }
            }
            Spacer(Modifier.weight(1f, fill = false).heightIn(min = if (linkedIn == null) 12.dp else 0.dp))
            Line(34.dp) { Title(card.name, number) }
            Line(24.dp) { StatusRow(line, colors) }
            Line(20.dp) {
                MorphCell(note(past)) {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = colors.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Line(48.dp) { Actions(line, past != null, unavailable, colors, action, onBackToToday) }
        }
    }
}

/** One line of the card at a fixed height, so a day change never moves the lines below it. */
@Composable
private fun Line(height: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.CenterStart) { content() }
}

/** "<Game> #", which no day changes, then the number, which does. */
@Composable
private fun Title(name: String, number: Int?) {
    val style = MaterialTheme.typography.headlineLarge.tabular()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (number == null) name else "$name #", style = style, maxLines = 1)
        MorphCell(number) { shown -> shown?.let { Text(count(it), style = style, maxLines = 1) } }
    }
}

/** The primary action, and "Back to today" while a past day is shown. */
@Composable
private fun Actions(
    line: DayLine,
    hasPast: Boolean,
    unavailable: Boolean,
    colors: GameCardColors,
    action: () -> Unit,
    onBackToToday: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = action,
            enabled = line != DayLine.Today(null),
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.fg, contentColor = colors.fill),
            modifier = Modifier
                .height(48.dp)
                .alpha(if (unavailable) UNAVAILABLE_ALPHA else 1f)
                .testTag("library.play"),
        ) {
            MorphCell(if (unavailable) "Try again" else actionLabel(line)) { Text(it, style = MaterialTheme.typography.labelLarge) }
        }
        MorphCell(hasPast) { shown ->
            if (shown) {
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
}

/** The eyebrow ("TODAY · THU, NOV 5" or "WED, OCT 14, 2026"), then a past day's tag pill. */
@Composable
private fun TopRow(face: TopFace, colors: GameCardColors) {
    Row(
        modifier = Modifier.then(if (face.date == null) Modifier else Modifier.testTag("library.day_card.day.${face.date}")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A past day's date gives way before the pill and the chip beside it, which say what the day is and what to do:
        // it sheds its weekday, then its year, rather than end in an ellipsis that hides which day it is.
        var shed by remember(face) { mutableIntStateOf(0) }
        val eyebrow = when {
            !face.past -> "TODAY" + face.date?.let { " · " + it.format(EyebrowDate) }.orEmpty()
            else -> face.date?.format(PastEyebrowDates[shed]).orEmpty()
        }
        Text(
            eyebrow.uppercase(Locale.US),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fg2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (face.past && it.hasVisualOverflow && shed < PastEyebrowDates.lastIndex) shed++ },
        )
        if (face.past) {
            Spacer(Modifier.width(10.dp))
            Box(
                // Keeps its whole width: the eyebrow date gives way to it and to the LinkedIn chip beside the row.
                modifier = Modifier
                    .height(24.dp)
                    .clip(PillShape)
                    .background(colors.fg.copy(alpha = TAG_WELL_ALPHA))
                    .padding(horizontal = 10.dp)
                    .testTag(if (face.archived) "library.day_card.tag.archived" else "library.day_card.tag.past"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (face.archived) "From the archive" else "Past puzzle",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The mark and its words, then the time that follows them: a solved day keeps "Solved" still and morphs its time. */
@Composable
private fun StatusRow(line: DayLine, colors: GameCardColors) {
    val (words, time) = statusParts(line)
    Row(verticalAlignment = Alignment.CenterVertically) {
        MorphCell(StatusLead(line, words), identity = { markKind(it.line) to it.words }) { lead ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (val shown = lead.line) {
                    is DayLine.Today -> StatusMark(shown.status, 16.dp, colors.fg)
                    is DayLine.Past -> PastDayMark(shown.status, 16.dp, colors.fg)
                }
                Spacer(Modifier.width(6.dp))
                if (lead.words == null) {
                    SkeletonLine(0.5f, 14.dp, colors.fg.copy(alpha = SKELETON_ALPHA))
                } else {
                    Text(lead.words, style = MaterialTheme.typography.bodyLarge.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        MorphCell(time) { shown -> shown?.let { Text(it, style = MaterialTheme.typography.bodyLarge.tabular(), maxLines = 1) } }
    }
}
