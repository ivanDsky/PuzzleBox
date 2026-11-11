package com.ivandsky.puzzlebox.app.debug

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.SecondaryButton

private const val SUDOKU_9X9 = "sudoku"

/** The unlisted 9x9 (ADR-0015) has no other way in: this row is it. */
@Composable
internal fun DebugGamesSection(openGame: (String) -> Unit, modifier: Modifier = Modifier) {
    SecondaryButton(
        text = "Play 9x9 Sudoku",
        onClick = { openGame(SUDOKU_9X9) },
        modifier = modifier.fillMaxWidth().testTag("debug.play.sudoku"),
    )
}
