package com.ivandsky.puzzlebox.games.queensui.trainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.LineLockRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queensui.PreviewDispatcher
import com.ivandsky.puzzlebox.games.queensui.QueensBoardState
import com.ivandsky.puzzlebox.games.queensui.QueensPlayBoard
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Frame [frame] of [demo] (clamped to the demo's frames): the real play board over the frame's position with the frame's
 * Spotlight on top, as the practice board draws them, but taking no input. A layer over the grid takes every touch, so
 * no tap or drag reaches the board. [compute] is the dispatcher the play board's hint stepper is given; the demo has no
 * hint button, so it never runs on it.
 */
@Composable
internal fun QueensIntroBoard(demo: QueensIntroDemo, frame: Int, compute: CoroutineDispatcher, modifier: Modifier = Modifier) {
    val shown = demo.frames[frame.coerceIn(0, demo.frames.lastIndex)]
    val scope = rememberCoroutineScope()
    val state = remember(demo, shown) {
        QueensBoardState(unplayedSession(demo.puzzle, shown.board), mode = HintMode.Stepwise(compute), scope = scope)
    }
    Box(modifier.aspectRatio(1f)) {
        QueensPlayBoard(state, Modifier.matchParentSize())
        SpotlightOverlay(shown.spotlight)
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
        )
    }
}

// Preview: a 4×4 board with its first row lit as evidence, then the same board after the step.
@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
internal fun QueensIntroBoardPreview() {
    val puzzle = QueensPuzzle(size = 4, regions = List(16) { it / 4 }, solution = listOf(Cell(1), Cell(7), Cell(8), Cell(14)))
    val step = QueensStep.Deduction(
        HintReason(HintReason.Kind.ROW, 0, LineLockRung),
        evidence = setOf(Cell(0), Cell(1)),
        result = QueensStep.Deduction.Result.Cross(setOf(Cell(2), Cell(3))),
    )
    val lit = QueensSpotlightMapper.map(step, puzzle, playId = 0).copy(callout = null)
    val demo = QueensIntroDemo(
        variant = 0,
        puzzle = puzzle,
        frames = listOf(
            QueensIntroDemo.Frame(QueensBoard(), lit, "Look at the lit squares."),
            QueensIntroDemo.Frame(QueensBoard(manual = setOf(Cell(2), Cell(3))), null, "The rest of the row is crossed out."),
        ),
    )
    PuzzleBoxTheme {
        QueensIntroBoard(demo, frame = 0, compute = PreviewDispatcher, modifier = Modifier.fillMaxWidth())
    }
}
