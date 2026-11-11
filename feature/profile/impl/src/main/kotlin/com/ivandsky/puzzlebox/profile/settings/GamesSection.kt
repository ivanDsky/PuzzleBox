package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Settings Games block: one switch per in-game option under its game's sub-heading, labelled exactly as the game's own overflow menu.
 * Stateless: it draws [state] and reports the new value of the tapped switch.
 */
@Composable
internal fun GamesSection(
    state: GamesSectionState,
    onQueensAutoCross: (Boolean) -> Unit,
    onSudokuPeerHighlight: (Boolean) -> Unit,
    onSudokuSameDigitHighlight: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("settings.games"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionTitle("Games")
        GameHeading("Queens")
        GameSwitchRow("Auto-cross", state.queensAutoCross, "settings.games.queens.autocross", onQueensAutoCross)
        GameHeading("Sudoku")
        GameSwitchRow(
            "Highlight row, column & box",
            state.sudokuPeerHighlight,
            "settings.games.sudoku.peer",
            onSudokuPeerHighlight,
        )
        GameSwitchRow(
            "Highlight same numbers",
            state.sudokuSameDigitHighlight,
            "settings.games.sudoku.samedigit",
            onSudokuSameDigitHighlight,
        )
    }
}

@Composable
private fun GameHeading(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The whole row is the tap target; the switch only shows the state, so a screen reader sees one switch per row. */
@Composable
private fun GameSwitchRow(label: String, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = null)
    }
}

private val MinRowHeight = 48.dp

@PuzzleBoxPreviews
@Composable
internal fun GamesSectionAllOnPreview() {
    PuzzleBoxTheme {
        GamesSection(GamesSectionState(), onQueensAutoCross = {}, onSudokuPeerHighlight = {}, onSudokuSameDigitHighlight = {})
    }
}

@PuzzleBoxPreviews
@Composable
internal fun GamesSectionMixedPreview() {
    PuzzleBoxTheme {
        GamesSection(
            GamesSectionState(queensAutoCross = false, sudokuSameDigitHighlight = false),
            onQueensAutoCross = {},
            onSudokuPeerHighlight = {},
            onSudokuSameDigitHighlight = {},
        )
    }
}
