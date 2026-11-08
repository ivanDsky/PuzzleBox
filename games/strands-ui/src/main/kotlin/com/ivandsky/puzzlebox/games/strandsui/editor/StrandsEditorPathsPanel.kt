package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath

private const val CREDIT =
    "Word list: ENABLE2K (Enhanced North American Benchmark LExicon) by Alan Beale and " +
        "M. Cooper, public domain. Source: enable2k.txt.gz, github.com/BartMassey/wordlists"

/** The touch-target floor for a path row. */
private val PATH_ROW_MIN_HEIGHT = 48.dp

/**
 * The clue field, one row per path and the word list credit. A row (`strands.editor.path.<i>`)
 * shows the path's word and length, carries `strands.editor.path.<i>.spangram` when it is the
 * spangram, and selects on a tap; a second tap lets the selection go.
 */
@Composable
internal fun StrandsPathsPanel(
    board: StrandsDraft,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onClue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StrandsClueField(board.clue, onClue)
        Column {
            board.paths.forEachIndexed { index, path ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PATH_ROW_MIN_HEIGHT)
                        .selectable(
                            selected = index == selected,
                            onClick = { onSelect(index.takeUnless { it == selected }) },
                            role = Role.Tab,
                        )
                        .padding(vertical = Spacing.xs)
                        .testTag("strands.editor.path.$index"),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = path.word ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (index == board.spangram) {
                        Text(
                            text = "★",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("strands.editor.path.$index.spangram"),
                        )
                    }
                    Text(path.cells.size.toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            text = CREDIT,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("strands.editor.credit"),
        )
    }
}

/** The theme clue (`strands.editor.clue`); the keyboard's Done or leaving the field commits it. */
@Composable
private fun StrandsClueField(clue: String, onCommit: (String) -> Unit) {
    var text by remember(clue) { mutableStateOf(clue) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        label = {
            Text(
                StrandsEditorCopy.CLUE_FIELD,
                Modifier.testTag("strands.editor.clue.label"),
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) onCommit(text) }
            .testTag("strands.editor.clue"),
    )
}

@PuzzleBoxPreviews
@Preview(name = "Spangram marked", showBackground = true, group = "Strands paths panel")
@Composable
private fun StrandsPathsPanelPreview() {
    val board = StrandsDraft(
        letters = "SOUP".padEnd(StrandsDraft.ROWS * StrandsDraft.COLS, StrandsDraft.BLANK),
        clue = "In the kitchen",
        paths = listOf(StrandsDraftPath(listOf(0, 1, 2, 3), "SOUP"), StrandsDraftPath(listOf(6, 7), null)),
        spangram = 0,
    )
    PuzzleBoxTheme {
        StrandsPathsPanel(board, selected = 1, onSelect = {}, onClue = {}, modifier = Modifier.padding(Spacing.md))
    }
}
