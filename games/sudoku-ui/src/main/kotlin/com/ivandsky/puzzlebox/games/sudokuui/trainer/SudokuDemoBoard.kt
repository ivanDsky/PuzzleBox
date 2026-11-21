package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuIntro
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudokuui.SudokuBoardState
import com.ivandsky.puzzlebox.games.sudokuui.SudokuStackedBoard

/**
 * Frame [frame] of [demo] on the real play board, drawn as [SudokuTrainer.Board] draws a practice board (the same grid
 * and keypad, notes showing) with no input: every touch is consumed above the board, so nothing selects or enters. The
 * frame's evidence is the Spotlight's lit area; a frame with none lights nothing.
 */
@Composable
internal fun SudokuDemoBoard(demo: SudokuIntroDemo, frame: Int, modifier: Modifier = Modifier) {
    val shown = demo.frame(frame)
    val state = remember(demo, shown) { SudokuBoardState(SudokuSession(ref = null, puzzle = demo.puzzle, board = shown.board)) }
    SudokuStackedBoard(state, modifier.consumingTouches().testTag("train.demo"), tuned = true, eraseBeside = true) { restyle ->
        shown.spotlight?.let { SpotlightOverlay(restyle(it)) }
    }
}

/** Takes every touch before the board under it sees it. */
private fun Modifier.consumingTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

// Preview: the evidence frame of the first demo a generated Mini Sudoku board holds, the board and lit squares an intro opens on.
private val PREVIEW_PUZZLE = SudokuPuzzle(
    SudokuGames.MINI.variant,
    "4....2..34....6...1......1.6.4..4.51".map { if (it == '.') 0 else it.digitToInt() },
    "451362623415546123132546215634364251".map { it.digitToInt() },
)

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 560, showBackground = true)
@Composable
internal fun SudokuDemoBoardPreview() {
    val demo = remember {
        SudokuTechniqueIds.all.firstNotNullOf { technique ->
            (0 until SudokuIntro.variantCount(technique)).firstNotNullOfOrNull { variant ->
                SudokuIntro.record(PREVIEW_PUZZLE, technique, variant)?.let { SudokuIntroDemo.of(PREVIEW_PUZZLE, it) }
            }
        }
    }
    PuzzleBoxTheme {
        SudokuDemoBoard(demo, frame = 1, Modifier.fillMaxSize())
    }
}
