package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.LocalOnFill
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.domain.TodayStatus

/** Every card's mark sits in this column, whatever the icon [size], so status text aligns. */
private val MarkColumn = 16.dp

/**
 * [status] as a [size] icon centred in the 16dp mark column (hub-card §Text slots): ring
 * when not started, half disc in progress, check when solved, retry arrow offline, and a
 * skeleton circle while loading (null).
 */
@Composable
fun StatusMark(status: TodayStatus?, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(MarkColumn).testTag(statusMarkTag(status)), contentAlignment = Alignment.Center) {
        if (status == null) {
            SkeletonCircle(size = size, color = tint.copy(alpha = SKELETON_ALPHA))
        } else {
            Icon(status.markIcon(), contentDescription = null, tint = tint, modifier = Modifier.size(size))
        }
    }
}

/** Test tag naming the mark's kind, so tests read state rather than pixels. */
internal fun statusMarkTag(status: TodayStatus?): String = "card.status_mark." + when (status) {
    null -> "loading"
    TodayStatus.NotStarted -> "not_started"
    is TodayStatus.InProgress -> "in_progress"
    is TodayStatus.Solved -> "solved"
    TodayStatus.Offline -> "offline"
}

private fun TodayStatus.markIcon(): ImageVector = when (this) {
    TodayStatus.NotStarted -> PuzzleIcons.StatusRing
    is TodayStatus.InProgress -> PuzzleIcons.StatusHalf
    is TodayStatus.Solved -> PuzzleIcons.Check
    TodayStatus.Offline -> PuzzleIcons.Retry
}

// Preview: every mark, hero size then tile size, on an accent fill.
@PuzzleBoxPreviews
@Composable
internal fun StatusMarkPreview() {
    val statuses = listOf(
        null,
        TodayStatus.NotStarted,
        TodayStatus.InProgress(192_000L),
        TodayStatus.Solved(42_000L),
        TodayStatus.Offline,
    )
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(AccentSlot.Violet.colors().accent).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(16.dp, 14.dp).forEach { size ->
                statuses.forEach { StatusMark(status = it, size = size, tint = LocalOnFill.current) }
            }
        }
    }
}
