package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize

private val SelectedBorder = 2.dp

/** "No hints", "1 hint", "3 hints". */
internal fun hintsLabel(hints: Int): String = when (hints) {
    0 -> "No hints"
    1 -> "1 hint"
    else -> "$hints hints"
}

@Composable
internal fun HistoryRowView(
    row: HistoryDayRow,
    selected: Boolean,
    onClick: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    val accent = row.brand.accent.colors()
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.row.${row.key}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
        border = if (selected) BorderStroke(SelectedBorder, accent.accent) else CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            GameGlyph(brand = row.brand, size = GameGlyphSize.Tile)
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.gameName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.edition,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.archived) {
                    ArchivePill(Modifier.padding(top = Spacing.xs).testTag("history.archived.${row.key}"))
                }
            }
            val record = row.record
            if (record == null) {
                AccentPill(
                    text = "Keep going",
                    brand = row.brand,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("history.unfinished.${row.key}"),
                )
            } else {
                if (row.isBest) BestPill(row.brand, Modifier.testTag("history.best.${row.key}"))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatDuration(record.time),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = hintsLabel(record.hints),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
