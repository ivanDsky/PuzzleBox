package com.ivandsky.puzzlebox.profile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
        SettingsSwitchRow("Auto-cross", state.queensAutoCross, "settings.games.queens.autocross", onQueensAutoCross)
        GameHeading("Sudoku")
        SettingsSwitchRow(
            "Highlight row, column & box",
            state.sudokuPeerHighlight,
            "settings.games.sudoku.peer",
            onSudokuPeerHighlight,
        )
        SettingsSwitchRow(
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
