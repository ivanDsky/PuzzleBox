package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession

/**
 * queens-08's hand-off to queens-09 (direction doc §8): every cell state × light/dark
 * in @PuzzleBoxPreviews panes, preview-first, device-confirm.
 * The panes use the fixture board (regions = rows) from the queens-ui test contract —
 * built from real [QueensSession] values through the same [QueensBoardState] the play
 * screen uses (stateless overload pattern: state holder in, no DI).
 */

/** Fixture board (regions = rows) with the known solution, main-source preview copy. */
private fun previewPuzzle() = com.ivandsky.puzzlebox.games.queens.QueensPuzzle(
    size = 4,
    regions = List(16) { it / 4 },
    solution = listOf(Cell(1), Cell(7), Cell(8), Cell(14)),
)

private val previewT0: java.time.Instant = java.time.Instant.parse("2026-11-02T00:00:00Z")

private fun previewSession(): QueensSession = QueensSession(ref = null, puzzle = previewPuzzle())

private fun previewState(
    queens: List<Int> = emptyList(),
    manual: List<Int> = emptyList(),
): QueensBoardState {
    var s = previewSession()
    manual.forEach { s = s.withCrossToggled(Cell(it), previewT0) }
    queens.forEach { s = s.withQueenPlaced(Cell(it), previewT0) }
    return QueensBoardState(initialSession = s)
}

private val solvedSession: QueensSession by lazy {
    listOf(Cell(1), Cell(7), Cell(8), Cell(14)).fold(previewSession()) { acc, c ->
        acc.withQueenPlaced(c, previewT0)
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
internal fun QueensBoardEmptyPreview() {
    PuzzleBoxTheme {
        QueensPlayBoard(previewState(), Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
internal fun QueensBoardManualXPreview() {
    PuzzleBoxTheme {
        QueensPlayBoard(previewState(manual = listOf(0, 5, 10, 15)), Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
internal fun QueensBoardAutoXPreview() {
    // The §3.3 provenance pair: MANUAL X (cells 15) next to AUTO fills from the queen.
    PuzzleBoxTheme {
        QueensPlayBoard(previewState(queens = listOf(1), manual = listOf(15)), Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
internal fun QueensBoardQueensPreview() {
    PuzzleBoxTheme {
        QueensPlayBoard(previewState(queens = listOf(1, 8)), Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
internal fun QueensBoardViolationPreview() {
    // A14 revised (fix round 6): violation stripes render ALWAYS ON — two same-row
    // queens conflict, no toggle involved.
    PuzzleBoxTheme {
        QueensPlayBoard(previewState(queens = listOf(1, 2)), Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 480, showBackground = true)
@Composable
internal fun QueensBoardSolvedPreview() {
    // Fix round 6: no win overlay on the board — the solved state hands off to the
    // result ROUTE at screen level (Zip's presentation). The board just rests.
    PuzzleBoxTheme {
        QueensPlayBoard(QueensBoardState(initialSession = solvedSession), Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Composable
internal fun QueensBoardGuidedPreview() {
    // Spec A12, lesson 1: faint queens on the answer squares still open — over the ×
    // on square 7 too — and none on the queen already placed.
    val state = previewState(queens = listOf(1), manual = listOf(7)).also { it.guide() }
    PuzzleBoxTheme {
        QueensPlayBoard(state, Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Expanded width", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
internal fun QueensBoardExpandedPreview() {
    PuzzleBoxTheme {
        QueensPlayBoard(previewState(queens = listOf(1), manual = listOf(15)), Modifier.fillMaxWidth())
    }
}
