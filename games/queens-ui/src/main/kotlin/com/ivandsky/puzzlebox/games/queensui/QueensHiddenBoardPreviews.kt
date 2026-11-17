package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard

/**
 * Queens' board before play as a puzzle's page shows it, light and dark: hidden, and plain for whoever may see
 * it. The puzzle travels with its queens, as a shared one does; [QueensReplayBoard.givens] leaves them behind.
 */
private val previewEnvelope: Puzzle by lazy {
    val puzzle = QueensPuzzle(
        size = 4,
        regions = List(16) { it / 4 },
        solution = listOf(Cell(1), Cell(7), Cell(8), Cell(14)),
    )
    Puzzle(QueensDefinition.id, queensVariantId(puzzle.size), QueensCodec.VERSION, QueensCodec.encode(puzzle))
}

@PuzzleBoxPreviews
@Composable
internal fun QueensHiddenBoardBlurredPreview() = HiddenBoardPane(sharp = false)

@PuzzleBoxPreviews
@Composable
internal fun QueensHiddenBoardSharpPreview() = HiddenBoardPane(sharp = true)

@Composable
private fun HiddenBoardPane(sharp: Boolean) {
    val board = QueensReplayBoard.givens(previewEnvelope) ?: return
    PuzzleBoxTheme { Surface { HiddenBoard(board, sharp = sharp, modifier = Modifier.padding(Spacing.lg)) } }
}
