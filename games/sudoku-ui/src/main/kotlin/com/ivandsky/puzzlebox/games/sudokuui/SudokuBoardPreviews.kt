package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

private val PREVIEW_SOLUTION = "463251521436142563356142234615615324".map { it.digitToInt() }
private val PREVIEW_GIVENS = "003050020406100000000002204010010300".map { it.digitToInt() }
private val PREVIEW_PUZZLE = SudokuPuzzle(SudokuGames.MINI.variant, PREVIEW_GIVENS, PREVIEW_SOLUTION)

private fun previewState(puzzle: SudokuPuzzle = PREVIEW_PUZZLE) = SudokuBoardState(SudokuSession(ref = null, puzzle = puzzle))

@Composable
private fun rememberPreviewController(definition: SudokuDefinition, puzzle: SudokuPuzzle, edition: Int?): SudokuPlayController {
    val scope = rememberCoroutineScope()
    return remember {
        SudokuPlayController(definition, PlayEnvironment(scope, {}, {})).apply {
            val envelope = Puzzle(definition.id, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
            adoptToday(envelope, SudokuPuzzleRef(definition.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), edition)))
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 480, showBackground = true)
@Composable
internal fun SudokuStackedBoardFreshPreview() {
    PuzzleBoxTheme {
        SudokuStackedBoard(previewState())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 480, showBackground = true)
@Composable
internal fun SudokuStackedBoardNotesPreview() {
    val state = previewState().apply {
        select(0)
        input(4)
        select(1)
        toggleNotesMode()
        input(1)
        input(6)
    }
    PuzzleBoxTheme {
        SudokuStackedBoard(state)
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 560, showBackground = true)
@Composable
internal fun SudokuStackedBoardClassicPreview() {
    PuzzleBoxTheme {
        SudokuStackedBoard(previewState(SudokuSamples.classic))
    }
}

@Preview(name = "Compact phone", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
internal fun SudokuPlayScreenSinglePanePreview() {
    val controller = rememberPreviewController(SudokuGames.MINI, PREVIEW_PUZZLE, edition = 450)
    PuzzleBoxTheme {
        SudokuPlayScreen(controller, PlayBoardStatus.Ready(banner = null), PlayLayout.SinglePane, {}, { _, _ -> })
    }
}

@Preview(name = "Expanded landscape", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
internal fun SudokuPlayScreenTwoPanePreview() {
    val controller = rememberPreviewController(SudokuGames.CLASSIC, SudokuSamples.classic, edition = null)
    PuzzleBoxTheme {
        SudokuPlayScreen(controller, PlayBoardStatus.Ready(banner = null), PlayLayout.TwoPane, {}, { _, _ -> })
    }
}
