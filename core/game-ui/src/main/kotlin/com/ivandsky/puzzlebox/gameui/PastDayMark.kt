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
import com.ivandsky.puzzlebox.domain.PastDayStatus

/**
 * A past day's [status] (spec A20) as a [size] icon in [StatusMark]'s 16dp column
 * (06-a §Day card): check when solved, half disc in progress, a phone when saved on this
 * device, a cloud when it needs a connection.
 */
@Composable
fun PastDayMark(status: PastDayStatus, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(MarkColumn).testTag(pastDayMarkTag(status)), contentAlignment = Alignment.Center) {
        Icon(status.markIcon(), contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/** Test tag naming the mark's kind, so tests read state rather than pixels. */
internal fun pastDayMarkTag(status: PastDayStatus): String = "card.past_mark." + when (status) {
    is PastDayStatus.Solved -> "solved"
    is PastDayStatus.InProgress -> "in_progress"
    PastDayStatus.Stored -> "saved"
    PastDayStatus.NeedsConnection -> "needs_connection"
}

private fun PastDayStatus.markIcon(): ImageVector = when (this) {
    is PastDayStatus.Solved -> PuzzleIcons.Check
    is PastDayStatus.InProgress -> PuzzleIcons.StatusHalf
    PastDayStatus.Stored -> PuzzleIcons.SavedOnDevice
    PastDayStatus.NeedsConnection -> PuzzleIcons.NeedsConnection
}

// Preview: every past-day mark, day-card size then row size, on an accent fill.
@PuzzleBoxPreviews
@Composable
internal fun PastDayMarkPreview() {
    val statuses = listOf(
        PastDayStatus.Solved(38_000L),
        PastDayStatus.InProgress(72_000L),
        PastDayStatus.Stored,
        PastDayStatus.NeedsConnection,
    )
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(AccentSlot.Teal.colors().accent).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(16.dp, 14.dp).forEach { size ->
                statuses.forEach { PastDayMark(status = it, size = size, tint = LocalOnFill.current) }
            }
        }
    }
}
