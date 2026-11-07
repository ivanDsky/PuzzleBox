package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.bestScoreText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LongDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private const val NONE = "—"

/** A date as the page writes it: "Mar 18, 2025". */
internal fun longDate(day: LocalDate): String = day.format(LongDate)

/**
 * One game's stats (06-a §Stats card): Solved · Hint-free · Average · Best tiles, then
 * when the player started, the archive's span and how many solves came from it. Before
 * any solve it shows only Solved and Best with a line saying archive days count too.
 * No streak: that is cross-game and lives on Home.
 */
@Composable
internal fun GameStatsCard(row: LibraryRowModel, modifier: Modifier = Modifier) {
    val stats = row.stats
    val empty = stats.solved == 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShapes.extraLarge)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("library.stats"),
    ) {
        Eyebrow("STATS")
        Spacer(Modifier.height(Spacing.md))
        val hintsFirst = stats.rankOrder == RankOrder.HintsThenTime
        val best = stats.best?.let { bestScoreText(it, stats.rankOrder) } ?: NONE
        val bestLabel = if (hintsFirst) "Best · hints first" else "Best"
        if (empty) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile("0", "Solved")
                StatTile(NONE, bestLabel)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                "No solves yet. Play today's, or any day back to #1 — archive solves count toward your best, never your streak.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("library.stats.empty"),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(count(stats.solved), "Solved")
                    StatTile(count(stats.hintFree), "Hint-free")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(stats.averageTimeMs?.let(::formatDuration) ?: NONE, "Average")
                    StatTile(best, bestLabel, small = hintsFirst && stats.best != null)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        KeyValue("Playing since", stats.playingSince?.let(::longDate) ?: "Not yet")
        row.progress?.let { archive ->
            KeyValue("Archive", "#1 · ${longDate(archive.range.start)} → #${count(archive.total)} today")
            val solves = if (archive.archivedSolves == 1) "1 solve" else "${count(archive.archivedSolves)} solves"
            KeyValue("From the archive", "$solves · best yes, streak never")
        }
    }
}

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun RowScope.StatTile(value: String, label: String, small: Boolean = false) {
    Column(
        Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.large)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = if (small) 22.sp else 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            ).tabular(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.tabular(),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
