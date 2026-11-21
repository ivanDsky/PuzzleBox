package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Right moves in a row that master a technique: the pips of the best-in-a-row row. */
internal const val MasteryRun = 5

private val PipSize = 16.dp

/** "Best in a row": the longest streak of this run as [MasteryRun] pips (capped there) and the number (not capped), the same whether or not it mastered. */
@Composable
internal fun BestRow(best: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {}.testTag("train.result.best"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Best in a row", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            repeat(MasteryRun) { index ->
                val on = index < best
                Box(
                    Modifier.size(PipSize).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Text("$best", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
    }
}

/** The four counts of the run in a 2 by 2 grid; none is hidden when it is 0. */
@Composable
internal fun StatTiles(result: ChapterResult, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Tile("${result.boardsDone} of ${result.boards}", "Boards done", Modifier.weight(1f))
            Tile("${result.rights}", "Right moves", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Tile("${result.shown}", "Shown with Show me", Modifier.weight(1f))
            Tile("${result.slips}", "Easier moves missed", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Surface(modifier.semantics(mergeDescendants = true) {}, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(Spacing.md)) {
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Board by board": one row per board, each with the one thing that happened on it and how many right moves it took. */
@Composable
internal fun BoardRows(rows: List<BoardRow>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            "Board by board",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = Spacing.sm).semantics { heading() },
        )
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BoardRowLine(row, Modifier.rise(delayMillis = index * RowStaggerMillis))
        }
    }
}

private const val RowStaggerMillis = 40

@Composable
private fun BoardRowLine(row: BoardRow, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (icon, fill, ink) = when (row.kind) {
        BoardRowKind.Clean -> Look(PuzzleIcons.Check, scheme.primaryContainer, scheme.onPrimaryContainer)
        BoardRowKind.Slip -> Look(PuzzleIcons.Hint, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        BoardRowKind.Shown -> Look(PuzzleIcons.Visibility, scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
    val label = when (row.kind) {
        BoardRowKind.Clean -> "All right"
        BoardRowKind.Slip -> "Easier move missed"
        BoardRowKind.Shown -> "Show me used"
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {}.testTag("train.result.row"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ink)
        }
        Text("Board ${row.board}: $label", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            if (row.moves == 1) "1 move" else "${row.moves} moves",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

private data class Look(val icon: ImageVector, val fill: Color, val ink: Color)
