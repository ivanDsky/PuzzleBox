package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath

/**
 * One chip per path (`strands.editor.path.<i>`), flowing in rows so eight paths fit the tablet's
 * side pane: the path's word and length, led by `strands.editor.path.<i>.spangram` when it is
 * the spangram; a tap selects the chip and a second tap lets it go. Each chip keeps the 48 dp
 * touch target. The clue and the word list credit are publish details ([StrandsPublishFields]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StrandsPathsPanel(
    board: StrandsDraft,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        board.paths.forEachIndexed { index, path ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index.takeUnless { it == selected }) },
                label = { Text("${path.word ?: "—"} · ${path.cells.size}", style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (index == board.spangram) {
                    {
                        Text(
                            text = "★",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("strands.editor.path.$index.spangram"),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.testTag("strands.editor.path.$index"),
            )
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Spangram marked", showBackground = true, group = "Strands paths panel")
@Composable
private fun StrandsPathsPanelPreview() {
    val board = StrandsDraft(
        letters = "SOUP".padEnd(StrandsDraft.ROWS * StrandsDraft.COLS, StrandsDraft.BLANK),
        clue = "",
        paths = listOf(StrandsDraftPath(listOf(0, 1, 2, 3), "SOUP"), StrandsDraftPath(listOf(6, 7), null)),
        spangram = 0,
    )
    PuzzleBoxTheme {
        StrandsPathsPanel(board, selected = 1, onSelect = {}, modifier = Modifier.padding(Spacing.md))
    }
}
