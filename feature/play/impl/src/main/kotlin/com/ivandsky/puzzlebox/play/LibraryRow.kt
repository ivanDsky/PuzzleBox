package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.LocalOnFill
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.CardSlot
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardColors
import com.ivandsky.puzzlebox.gameui.GameCardSurface
import com.ivandsky.puzzlebox.gameui.GameMotif
import com.ivandsky.puzzlebox.gameui.SkeletonLine
import com.ivandsky.puzzlebox.gameui.StatusMark
import com.ivandsky.puzzlebox.gameui.todayStatusLine
import java.util.Locale

private val NameHeight = 28.dp
private val TodayHeight = 20.dp
private val ProgressHeight = 18.dp
private val BarHeight = 6.dp
private val BarEndInset = 64.dp
private val BarGap = 8.dp
private val MarkSize = 14.dp
private val OutlineWidth = 3.dp
private val OutlineGap = 3.dp
private const val MIN_BAR_FILL = 0.015f
private const val TRACK_ALPHA = 0.24f
internal const val SECONDARY_ALPHA = 0.82f
internal const val MOTIF_ALPHA = 0.18f
internal const val SKELETON_ALPHA = 0.16f

/**
 * A library card's paint: every row stays in its brand fill, offline too, because a
 * saved puzzle still plays (06-a §States); only today's line says it can't load.
 */
@Composable
internal fun libraryCardColors(brand: GameBrand): GameCardColors {
    val onFill = LocalOnFill.current
    return GameCardColors(
        fill = brand.accent.colors().accent,
        fg = onFill,
        fg2 = onFill.copy(alpha = SECONDARY_ALPHA),
        motif = onFill,
        motifAlpha = MOTIF_ALPHA,
    )
}

/**
 * One game's library row (06-a §Row): brand card, motif cropped top-end, the name, today's
 * "#N today · status" line with its mark, and the archive's solved count over a thin bar.
 * [selected] (the tablet's open page) draws an outline just outside the card. A loading
 * row (null status) draws skeletons in the same slots.
 */
@Composable
internal fun LibraryRow(
    row: LibraryRowModel,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = row.card
    val colors = libraryCardColors(card.brand)
    val outline = MaterialTheme.colorScheme.onSurface
    GameCardSurface(
        colors = colors,
        shape = AppShapes.large,
        onClick = onClick,
        modifier = modifier
            .drawBehind {
                if (!selected) return@drawBehind
                val inset = OutlineGap.toPx() + OutlineWidth.toPx() / 2
                val radius = 20.dp.toPx() + inset
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(-inset, -inset),
                    size = Size(size.width + 2 * inset, size.height + 2 * inset),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(OutlineWidth.toPx()),
                )
            }
            .fillMaxWidth()
            .height(if (compact) 120.dp else 128.dp)
            .semantics { this.selected = selected }
            .testTag("library.row.${card.game.value}"),
        motif = {
            GameMotif(card.brand, 150.dp, 1.6f, Alignment.TopEnd, DpOffset(22.dp, (-36).dp), colors.motif, colors.motifAlpha)
        },
    ) {
        if (compact) {
            Icon(
                PuzzleIcons.Chevron,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.lg).size(24.dp),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardSlot(card.name, NameHeight) { name ->
                Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CardSlot(card.status, TodayHeight, Modifier.testTag("library.row.today.${card.game.value}")) { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusMark(status, MarkSize, colors.fg)
                    Spacer(Modifier.width(6.dp))
                    if (status == null) {
                        SkeletonLine(0.6f, 12.dp, colors.fg.copy(alpha = SKELETON_ALPHA))
                    } else {
                        Text(
                            todayLine(row.today, status),
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            ProgressLine(row.progress, loading = card.status == null, colors = colors)
        }
    }
}

@Composable
private fun ProgressLine(progress: ArchiveProgress?, loading: Boolean, colors: GameCardColors) {
    CardSlot(loading to progress, ProgressHeight + BarGap + BarHeight) { (skeleton, shown) ->
        Column(Modifier.fillMaxWidth()) {
            when {
                skeleton -> SkeletonLine(0.5f, 10.dp, colors.fg.copy(alpha = SKELETON_ALPHA))
                shown != null && shown.total > 0 -> {
                    Text(
                        "${count(shown.solved)} of ${count(shown.total)} solved · back to #1",
                        style = MaterialTheme.typography.labelLarge.tabular(),
                        color = colors.fg2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(ProgressHeight),
                    )
                    Spacer(Modifier.height(BarGap))
                    val fraction = shown.solved.toFloat() / shown.total
                    Box(
                        Modifier.padding(end = BarEndInset).fillMaxWidth().height(BarHeight)
                            .background(colors.fg.copy(alpha = TRACK_ALPHA), PillShape),
                    ) {
                        if (shown.solved > 0) {
                            Box(
                                Modifier.fillMaxWidth(fraction.coerceIn(MIN_BAR_FILL, 1f)).height(BarHeight)
                                    .background(colors.fg, PillShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "#N today · status": the row's own words for the not-started and offline days. */
internal fun todayLine(number: Int?, status: TodayStatus): String {
    val words = when (status) {
        TodayStatus.NotStarted -> "Today's puzzle"
        TodayStatus.Offline -> "Can't load it · tap to try again"
        is TodayStatus.InProgress, is TodayStatus.Solved -> todayStatusLine(status)
    }
    return if (number == null) words else "#${count(number)} today · $words"
}

/** A count with thousands separators, the same on every locale. */
internal fun count(n: Int): String = String.format(Locale.US, "%,d", n)
