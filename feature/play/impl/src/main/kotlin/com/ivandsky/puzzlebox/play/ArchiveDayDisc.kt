package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalOnFill
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.PastDayStatus
import java.time.LocalDate

private const val IN_PROGRESS_HALF_ALPHA = 0.4f
private const val SAVED_RING_ALPHA = 0.6f
private const val NOT_OPENED_ALPHA = 0.05f
private const val OFFLINE_ALPHA = 0.45f
private const val OUTSIDE_ALPHA = 0.28f
private const val NUMBER_ALPHA = 0.78f
private val ProgressRing = 2.5.dp
private val SavedRing = 2.dp
private val SelectedRing = 2.5.dp
private val SelectedGap = 3.dp

/** How far the selected ring's outer edge reaches past its disc on every side. */
internal val SelectedRingRoom = SelectedGap + SelectedRing
private val TodayBarWidth = 16.dp
private val TodayBarHeight = 3.dp
private val TodayBarGap = 7.dp

/** A disc's look (06-a cell state table); [key] names it for tests (state, not pixels). */
internal enum class DiscKind(val key: String) {
    Solved("solved"),
    InProgress("in_progress"),
    Saved("saved"),
    NotOpened("not_opened"),
    Outside("outside"),
}

/** [day]'s disc, or [DiscKind.Outside] for a day before #1, after today or in another month. */
internal fun discKind(day: ArchiveDay?): DiscKind = when (day?.status) {
    null -> DiscKind.Outside
    is PastDayStatus.Solved -> DiscKind.Solved
    is PastDayStatus.InProgress -> DiscKind.InProgress
    PastDayStatus.Stored -> DiscKind.Saved
    PastDayStatus.NeedsConnection -> DiscKind.NotOpened
}

/**
 * Paints [kind] filling the scope: solved an [accent] disc, in progress an inset [ring]
 * with its right half at 40%, saved a [savedRing] at 60%, not opened a 5% [neutral] disc.
 * Shared by the grid's discs and the legend's swatches.
 */
internal fun DrawScope.drawDisc(kind: DiscKind, accent: Color, neutral: Color, ring: Dp, savedRing: Dp) {
    val radius = size.minDimension / 2
    when (kind) {
        DiscKind.Solved -> drawCircle(accent, radius)
        DiscKind.InProgress -> {
            val width = ring.toPx()
            drawCircle(accent, radius - width / 2, style = Stroke(width))
            val inner = radius - width
            drawArc(
                color = accent.copy(alpha = IN_PROGRESS_HALF_ALPHA),
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - inner, center.y - inner),
                size = Size(inner * 2, inner * 2),
            )
        }
        DiscKind.Saved -> {
            val width = savedRing.toPx()
            drawCircle(accent.copy(alpha = SAVED_RING_ALPHA), radius - width / 2, style = Stroke(width))
        }
        DiscKind.NotOpened -> drawCircle(neutral.copy(alpha = NOT_OPENED_ALPHA), radius)
        DiscKind.Outside -> Unit
    }
}

/**
 * One calendar cell (06-a §Month calendar): a [disc]-sized disc centred in [cell], the
 * day number over "#N" in the state's colours. [today] adds a bar under the disc and
 * [selected] a ring just outside it; offline a never-opened disc dims. An outside day
 * (null [day]) is only a faint number and takes no tap; others call [onClick].
 */
@Composable
internal fun ArchiveDayDisc(
    date: LocalDate,
    day: ArchiveDay?,
    today: Boolean,
    selected: Boolean,
    offline: Boolean,
    accent: Color,
    cell: Dp,
    disc: Dp,
    reduced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = discKind(day)
    val scheme = MaterialTheme.colorScheme
    val mark = scheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val clickable = if (day == null) {
        Modifier
    } else {
        Modifier
            .testTag("library.calendar.day.$date")
            .semantics { stateDescription = kind.key }
            .selectable(selected = selected, interactionSource = interaction, indication = null, onClick = onClick)
            .pressScale(interaction, reduced)
    }
    Box(
        modifier = modifier
            .size(width = disc, height = cell)
            .then(clickable)
            .drawBehind {
                val discBottom = (size.height + disc.toPx()) / 2
                if (today) {
                    // The bar sits in the gap to the next row's disc, so a phone's tighter cell lifts it.
                    val gap = minOf(TodayBarGap, cell - disc - TodayBarHeight).toPx()
                    drawRoundRect(
                        color = mark,
                        topLeft = Offset(center.x - TodayBarWidth.toPx() / 2, discBottom + gap),
                        size = Size(TodayBarWidth.toPx(), TodayBarHeight.toPx()),
                        cornerRadius = CornerRadius(TodayBarHeight.toPx() / 2),
                    )
                }
                if (selected) {
                    val width = SelectedRing.toPx()
                    drawCircle(mark, disc.toPx() / 2 + SelectedGap.toPx() + width / 2, style = Stroke(width))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val dim = kind == DiscKind.NotOpened && offline
        Box(
            modifier = Modifier
                .size(disc)
                .graphicsLayer { alpha = if (dim) OFFLINE_ALPHA else 1f }
                .drawBehind { drawDisc(kind, accent, mark, ProgressRing, SavedRing) },
            contentAlignment = Alignment.Center,
        ) {
            val text = when (kind) {
                DiscKind.Solved -> LocalOnFill.current
                DiscKind.InProgress, DiscKind.Saved -> scheme.onSurface
                DiscKind.NotOpened -> scheme.onSurfaceVariant
                DiscKind.Outside -> scheme.onSurface.copy(alpha = OUTSIDE_ALPHA)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold).tabular(),
                    color = text,
                )
                if (day != null) {
                    Text(
                        "#${day.number}",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp, lineHeight = 11.sp).tabular(),
                        color = text.copy(alpha = NUMBER_ALPHA),
                    )
                }
            }
        }
    }
}
