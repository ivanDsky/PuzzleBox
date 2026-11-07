package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.editor.QueensCrownIssue
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraftAnalyzer
import com.ivandsky.puzzlebox.games.queens.editor.QueensPublishGates
import com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette

private val FixRowMinHeight = 48.dp
private val FixSwatchSize = 28.dp
private val FixSwatchDash = 2.dp

/**
 * One fix-list row: a reason, block or crown-issue [id] about [region] (or [NO_REGION]), with a
 * [count] (unpainted cells, painted regions, pieces or crowns) and the [cells] Select picks.
 */
internal data class QueensFixRow(
    val id: String,
    val count: Int = 0,
    val region: Int = NO_REGION,
    val cells: Set<Int> = emptySet(),
) {
    internal companion object {
        const val NO_REGION: Int = -1

        /**
         * The rows for [draft] under [verdict] and [blocks], worst first (03 b handoff): the
         * Incomplete reason, then the board's own blocks (split regions, needs guessing) and,
         * when the draft has answer crowns, its crown issues. Blocks Queens does not own
         * (title, test solve, …) belong to the readiness line, not here.
         */
        fun of(draft: QueensDraft, verdict: Verdict, blocks: List<PublishBlock>): List<QueensFixRow> = buildList {
            if (verdict is Verdict.Incomplete) {
                val reason = verdict.reason
                val cells = if (reason.id == QueensDraftAnalyzer.UNPAINTED) {
                    draft.regions.indices.filterTo(LinkedHashSet()) { draft.regions[it] == QueensDraft.UNPAINTED }
                } else {
                    emptySet()
                }
                add(QueensFixRow(reason.id, count = reason.count, cells = cells))
            }
            blocks.filter { it.id in QueensPublishGates.blockIds }.forEach { block ->
                val region = if (block.subject == PublishBlock.NO_SUBJECT) NO_REGION else block.subject
                add(QueensFixRow(block.id, count = block.count, region = region, cells = block.cells))
            }
            QueensCrownIssue.issuesOf(draft).forEach { issue ->
                add(QueensFixRow(issue.id, count = issue.cells.count { it in draft.crowns }, region = issue.region, cells = issue.cells))
            }
        }
    }
}

/** The row's copy on a [size]×[size] board; null for an id Queens has no copy for. */
internal fun QueensFixRow.text(size: Int): String? = when (id) {
    QueensDraftAnalyzer.UNPAINTED -> if (count == 1) "1 cell unpainted" else "$count cells unpainted"
    QueensDraftAnalyzer.REGIONS ->
        if (count < size) "$count of $size regions — ${size - count} more needed" else "$count regions — only $size allowed"
    PublishBlock.SPLIT_REGION -> "Region ${letterOf(region)} is in $count pieces"
    PublishBlock.NEEDS_GUESSING -> "Logic alone can't finish it — players would have to guess"
    QueensCrownIssue.DOUBLE -> "Region ${letterOf(region)} has $count crowns"
    QueensCrownIssue.MISSING -> "Region ${letterOf(region)} has no crown"
    else -> null
}

private fun letterOf(region: Int): String = ('A' + region).toString()

/**
 * The verdict card's Queens fix list over [controller]'s draft: one row per problem
 * ([QueensFixRow.of]); a row's **Select** switches to Paint and selects its cells, then calls
 * [onSelect] (the host closes the card), so the fix is one ring pick or one drag away. Draws
 * nothing when there is nothing to fix.
 */
@Composable
internal fun QueensFixList(
    controller: QueensEditorController,
    verdict: Verdict,
    blocks: List<PublishBlock>,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorDraft by controller.draft.collectAsState()
    val draft = editorDraft.state.draft as QueensDraft
    val rows = remember(draft, verdict, blocks) { QueensFixRow.of(draft, verdict, blocks) }
    QueensFixRows(rows, draft.size, onSelect = { cells ->
        controller.select(cells)
        onSelect()
    }, modifier = modifier)
}

/**
 * [rows] as a divided list, each tagged `queens.editor.fix.<id>` with its copy merged in; a
 * row with cells has a Select (`queens.editor.fix.<id>.select`) that reports them to [onSelect].
 * A row without copy is not drawn.
 */
@Composable
internal fun QueensFixRows(
    rows: List<QueensFixRow>,
    size: Int,
    onSelect: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rows.mapNotNull { row -> row.text(size)?.let { row to it } }
    if (shown.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag("queens.editor.fix")) {
        shown.forEachIndexed { index, (row, text) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FixRow(row, text, onSelect)
        }
    }
}

@Composable
private fun FixRow(row: QueensFixRow, text: String, onSelect: (Set<Int>) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FixRowMinHeight)
            .testTag("queens.editor.fix.${row.id}")
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(row.region)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (row.cells.isNotEmpty()) {
            TextButton(
                onClick = { onSelect(row.cells) },
                modifier = Modifier.testTag("queens.editor.fix.${row.id}.select"),
                colors = ButtonDefaults.textButtonColors(contentColor = AccentSlot.Violet.colors().accent),
            ) { Text("Select") }
        }
    }
}

/** The region's fill and letter, or a dashed blank square for a row about no region. */
@Composable
private fun Swatch(region: Int) {
    val base = Modifier.size(FixSwatchSize).clearAndSetSemantics {}
    if (region == QueensFixRow.NO_REGION) {
        Box(base.border(FixSwatchDash, MaterialTheme.colorScheme.outline, AppShapes.extraSmall))
        return
    }
    val fill = QueensRegionPalette.fill(region, isSystemInDarkTheme())
    val ink = QueensRegionPalette.onInkFor(fill, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.scrim)
    Box(base.background(fill, AppShapes.extraSmall), contentAlignment = Alignment.Center) {
        Text(letterOf(region), color = ink, style = MaterialTheme.typography.titleSmall)
    }
}

@PuzzleBoxPreviews
@Composable
private fun QueensFixRowsPreview() {
    PuzzleBoxTheme {
        Surface {
            QueensFixRows(
                rows = listOf(
                    QueensFixRow(QueensDraftAnalyzer.UNPAINTED, count = 3, cells = setOf(1, 2, 3)),
                    QueensFixRow(PublishBlock.SPLIT_REGION, count = 2, region = 2, cells = setOf(4, 9)),
                    QueensFixRow(QueensCrownIssue.DOUBLE, count = 2, region = 0, cells = setOf(0, 1)),
                    QueensFixRow(QueensCrownIssue.MISSING, region = 3, cells = setOf(10, 11)),
                ),
                size = 4,
                onSelect = {},
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun QueensFixRowsRegionsPreview() {
    PuzzleBoxTheme {
        Surface {
            QueensFixRows(
                rows = listOf(QueensFixRow(QueensDraftAnalyzer.REGIONS, count = 3), QueensFixRow(PublishBlock.NEEDS_GUESSING)),
                size = 4,
                onSelect = {},
            )
        }
    }
}
