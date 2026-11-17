package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.BoardGrid

// Previews of the hidden board — internal, light + dark inside PuzzleBoxTheme, over a stand-in board: this module
// knows no game. Each game's own board is pinned by its HiddenBoardGoldenScreenshots.

private const val REGIONS = 3

/** A stand-in board with coloured regions and a digit in every other cell: something worth hiding. */
internal fun sampleGivensBoard(rows: Int = 6, cols: Int = 6): GivensBoard = GivensBoard(rows, cols) { modifier ->
    val regions = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
    )
    BoardGrid(
        rows = rows,
        cols = cols,
        modifier = modifier,
        cellContent = { row, col ->
            Box(Modifier.fillMaxSize().background(regions[(row / 2 + col / 2) % REGIONS]), Alignment.Center) {
                if ((row + col) % 2 == 0) Text("${(row * cols + col) % 9 + 1}", style = MaterialTheme.typography.titleLarge)
            }
        },
    )
}

@Composable
private fun PreviewPage(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Surface {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun HiddenBoardPreview() {
    PreviewPage { HiddenBoard(sampleGivensBoard(), sharp = false) }
}

@PuzzleBoxPreviews
@Composable
internal fun HiddenBoardSharpPreview() {
    PreviewPage { HiddenBoard(sampleGivensBoard(), sharp = true) }
}

@PuzzleBoxPreviews
@Composable
internal fun HiddenBoardWidePreview() {
    PreviewPage {
        HiddenBoard(sampleGivensBoard(rows = 4, cols = 8), sharp = false)
        HiddenBoard(sampleGivensBoard(rows = 8, cols = 6), sharp = false)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun HiddenBoardLoadingPreview() {
    PreviewPage { HiddenBoardLoading() }
}
