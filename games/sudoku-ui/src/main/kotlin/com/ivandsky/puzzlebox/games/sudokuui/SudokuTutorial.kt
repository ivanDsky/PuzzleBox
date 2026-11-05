package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.TutorialStep
import java.time.LocalDate

/** Tutorial refs are custom boards: a fixed day and no puzzle number. */
private val TUTORIAL_DAY: LocalDate = LocalDate.of(2026, 1, 1)

/** Known solutions per board size: LinkedIn Mini Sudoku #450 and the textbook classic. */
private val SOLUTIONS: Map<Int, String> = mapOf(
    6 to "463251521436142563356142234615615324",
    9 to "534678912672195348198342567859761423426853791713924856961537284287419635345286179",
)

/**
 * Two lessons for [definition], each a solved board with one open cell. The second lesson
 * relabels the digits (d -> d+1, wrapping), which keeps every row, column and box valid.
 */
fun sudokuTutorial(definition: SudokuDefinition): List<TutorialStep> {
    val size = definition.variant.geometry.size
    val solution = requireNotNull(SOLUTIONS[size]) { "no tutorial board for size $size" }.map { it.digitToInt() }
    val relabeled = solution.map { it % size + 1 }
    return listOf(
        lesson(definition, "Each row holds every digit once. Fill the open cell.", solution, open = 0),
        lesson(definition, "Each box holds every digit once, too.", relabeled, open = size * size / 2 + 1),
    )
}

private fun lesson(definition: SudokuDefinition, instruction: String, solution: List<Int>, open: Int): TutorialStep {
    val puzzle = SudokuPuzzle(
        variant = definition.variant,
        givens = solution.mapIndexed { idx, digit -> if (idx == open) 0 else digit },
        solution = solution,
    )
    return TutorialStep(
        instruction = instruction,
        puzzle = Puzzle(definition.id, definition.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle)),
        ref = SudokuPuzzleRef(definition.id, TUTORIAL_DAY, null),
    )
}
