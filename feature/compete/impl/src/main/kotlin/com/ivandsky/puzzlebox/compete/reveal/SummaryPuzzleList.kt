package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry

/**
 * The puzzles table: a "Puzzles" header with the count, then an outlined card of column heads over one row per puzzle in
 * order. Every row and the heads share one [TableMetrics], so the two time columns, the ticks and the margin bars sit
 * at the same x down the whole card. [wide] is the tablet table (one line per puzzle); otherwise the phone's, where the
 * margin bar and times drop under the puzzle's name. Rows arrive one after another, the stagger capped by [ResultBeats].
 */
@Composable
internal fun SummaryPuzzleList(summary: DuelSummary, brands: GamePlayRegistry, wide: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Puzzles", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "${summary.rows.size} played",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        OutlinedCard(Modifier.fillMaxWidth().testTag("reveal.summary.puzzles")) {
            BoxWithConstraints {
                val metrics = remember(wide, maxWidth) { TableMetrics.of(compact = !wide, tableWidth = maxWidth) }
                Column {
                    PuzzleTableHeader(summary, metrics)
                    summary.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PuzzleTableRow(row, brands.brandOf(row.gameId), metrics, index)
                    }
                }
            }
        }
    }
}
