package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.CardSlot
import com.ivandsky.puzzlebox.gameui.GameCardSurface
import com.ivandsky.puzzlebox.gameui.GameMotif
import com.ivandsky.puzzlebox.gameui.SkeletonLine
import com.ivandsky.puzzlebox.gameui.StatusMark
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EyebrowDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

// "Try again" reads unavailable while offline, yet stays a tap target: its tap is the retry.
private const val UNAVAILABLE_ALPHA = 0.5f

/**
 * Today's day card (06-a §Day card): the brand card with the big motif bottom-end, the
 * "TODAY · EEE, MMM d" eyebrow, "<Game> #N", today's status with its mark, the streak
 * note and the one action. [onPlay] opens today's board; offline the action is
 * "Try again" and its tap is [onRetry]. [today] is the game's day (null before it loads).
 */
@Composable
internal fun ArchiveDayCard(
    row: LibraryRowModel,
    today: LocalDate?,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = row.card
    val status = card.status
    val colors = libraryCardColors(card.brand)
    val offline = status == TodayStatus.Offline
    GameCardSurface(
        colors = colors,
        shape = AppShapes.extraLarge,
        onClick = if (offline) onRetry else onPlay,
        modifier = modifier.fillMaxWidth().heightIn(min = 196.dp).testTag("library.day_card"),
        motif = {
            GameMotif(card.brand, 300.dp, 1.4f, Alignment.BottomEnd, DpOffset(46.dp, 80.dp), colors.motif, colors.motifAlpha)
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(
                "TODAY" + today?.let { " · " + it.format(EyebrowDate).uppercase(Locale.US) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fg2,
                modifier = Modifier.height(24.dp),
            )
            Spacer(Modifier.weight(1f, fill = false).heightIn(min = 12.dp))
            CardSlot(row.today, 34.dp) { number ->
                Text(
                    card.name + number?.let { " #${count(it)}" }.orEmpty(),
                    style = MaterialTheme.typography.headlineLarge.tabular(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CardSlot(status, 24.dp) { shown ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusMark(shown, 16.dp, colors.fg)
                    Spacer(Modifier.width(6.dp))
                    if (shown == null) {
                        SkeletonLine(0.5f, 14.dp, colors.fg.copy(alpha = SKELETON_ALPHA))
                    } else {
                        Text(dayStatusLine(shown), style = MaterialTheme.typography.bodyLarge.tabular(), maxLines = 1)
                    }
                }
            }
            Text(
                "Counts toward your streak",
                style = MaterialTheme.typography.labelLarge,
                color = colors.fg2,
                modifier = Modifier.height(20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = if (offline) onRetry else onPlay,
                enabled = status != null,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.fg, contentColor = colors.fill),
                modifier = Modifier
                    .height(48.dp)
                    .alpha(if (offline) UNAVAILABLE_ALPHA else 1f)
                    .testTag("library.play"),
            ) {
                Text(actionLabel(status), style = MaterialTheme.typography.labelLarge)
            }
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

private fun actionLabel(status: TodayStatus?): String = when (status) {
    null, TodayStatus.NotStarted -> "Play"
    is TodayStatus.InProgress -> "Keep going"
    is TodayStatus.Solved -> "Play again"
    TodayStatus.Offline -> "Try again"
}
