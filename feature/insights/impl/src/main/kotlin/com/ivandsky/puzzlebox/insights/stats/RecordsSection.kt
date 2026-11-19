package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRecords
import com.ivandsky.puzzlebox.model.GameId

/**
 * The Records section: each game's fastest solve, the longest streak and the longest hint-free run, as one block of
 * rows. A row with a solve behind it opens that solve through [onOpenSolve] (A13): its replay when it has one, the
 * day otherwise (an import). A row with nothing to open (a streak held only by freezes) is plain.
 */
@Composable
internal fun RecordsSection(
    state: SectionState<StatsRecords>,
    gameName: (GameId) -> String,
    onOpenSolve: (SolveRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsSectionFrame(title = "Records", state = state, tag = "stats.records", modifier = modifier) { records ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("stats.records.rows"),
        ) {
            val rows = recordRows(records, gameName)
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
                RecordRow(row, onOpenSolve)
            }
        }
    }
}

/** One line of the block: what it is, when, its value, and the [solve] behind it. */
private data class RecordLine(val tag: String, val label: String, val detail: String, val value: String, val solve: SolveRef?)

private fun recordRows(records: StatsRecords, gameName: (GameId) -> String): List<RecordLine> = buildList {
    records.fastest.forEach { fastest ->
        val solve = SolveRef(fastest.game, fastest.solve.day, fastest.solve.puzzleNumber, fastest.solve.hasReplay)
        add(
            RecordLine(
                tag = "stats.records.fastest.${fastest.game.value}",
                label = "Fastest ${gameName(fastest.game)}",
                detail = fastest.solve.day.datedLabel() + if (solve.hasReplay) "" else " · No replay",
                value = formatDuration(fastest.solve.timeMs),
                solve = solve,
            ),
        )
    }
    records.longestStreak?.let { streak ->
        add(
            RecordLine(
                tag = "stats.records.streak",
                label = "Longest streak",
                detail = "${streak.run.start.datedLabel()} – ${streak.run.end.datedLabel()}",
                value = plural(streak.run.days, "day"),
                solve = streak.solve,
            ),
        )
    }
    records.longestHintFreeRun?.let { run ->
        add(
            RecordLine(
                tag = "stats.records.hintFree",
                label = "Longest hint-free run",
                detail = "${run.start.datedLabel()} – ${run.last.day.datedLabel()}",
                value = plural(run.solves, "solve"),
                solve = run.last,
            ),
        )
    }
}

@Composable
private fun RecordRow(line: RecordLine, onOpenSolve: (SolveRef) -> Unit) {
    val solve = line.solve
    val open = if (solve == null) {
        Modifier
    } else {
        Modifier.clickable(onClickLabel = if (solve.hasReplay) "Watch replay" else "Open day", role = Role.Button) { onOpenSolve(solve) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .then(open)
            .padding(start = Spacing.lg)
            .semantics(mergeDescendants = true) {}
            .testTag(line.tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(text = line.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = line.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text = line.value, style = MaterialTheme.typography.titleMedium.tabular(), color = MaterialTheme.colorScheme.onSurface)
        // A fixed slot, so the values of rows that open something and rows that do not line up.
        Row(Modifier.size(IconSlot), verticalAlignment = Alignment.CenterVertically) {
            if (solve != null) {
                Icon(
                    if (solve.hasReplay) PuzzleIcons.Play else PuzzleIcons.Chevron,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.xs).size(IconSlot - Spacing.xs * 2),
                )
            }
        }
    }
}

private val RowMinHeight = 56.dp
private val IconSlot = 40.dp
