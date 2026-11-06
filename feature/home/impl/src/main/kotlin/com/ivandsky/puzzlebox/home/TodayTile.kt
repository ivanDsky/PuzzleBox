package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize

private val RingSize = 20.dp
private val RingStroke = 2.dp
private const val OFFLINE_ALPHA = 0.5f

/**
 * One game of today's set (A10, identity §2.8): its glyph tile, name, status ring and the
 * time (or how long so far), all in the game's brand. The ring is a hairline when ready,
 * the accent while in progress (the tile's edge too) and a filled ✓ once solved. An
 * Offline tile (A12) is dimmed, reads "Offline" and is disabled: it never opens a dead
 * end. Tapping any other tile is [onClick].
 */
@Composable
internal fun TodayTile(
    entry: HomeUiState.TodayRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = entry.brand.accent.colors()
    val status = entry.status
    val offline = status == TodayStatus.Offline
    OutlinedCard(
        onClick = onClick,
        enabled = !offline,
        modifier = modifier.then(if (offline) Modifier.alpha(OFFLINE_ALPHA) else Modifier),
        shape = MaterialTheme.shapes.large,
        border = if (status is TodayStatus.InProgress) {
            BorderStroke(RingStroke, accent.accent)
        } else {
            CardDefaults.outlinedCardBorder(enabled = !offline)
        },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                GameGlyph(entry.brand, GameGlyphSize.Tile)
                StatusRing(status, accent)
            }
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status?.let(::tileLine).orEmpty(),
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusRing(status: TodayStatus?, accent: GameAccent) {
    val ring = Modifier.size(RingSize)
    when (status) {
        is TodayStatus.Solved -> Box(
            modifier = ring.background(accent.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PuzzleIcons.Check,
                contentDescription = null,
                tint = accent.onAccent,
                modifier = Modifier.size(14.dp),
            )
        }
        is TodayStatus.InProgress -> Box(ring.border(RingStroke, accent.accent, CircleShape))
        TodayStatus.NotStarted -> Box(ring.border(RingStroke, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        // Offline, or not known yet: no ring.
        TodayStatus.Offline, null -> Box(ring)
    }
}

/** The tile's second line: the solve time, the time so far, or ready / offline. */
private fun tileLine(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Ready"
    is TodayStatus.InProgress -> "${formatDuration(status.elapsedMs)} so far"
    is TodayStatus.Solved -> formatDuration(status.timeMs)
    TodayStatus.Offline -> "Offline"
}
