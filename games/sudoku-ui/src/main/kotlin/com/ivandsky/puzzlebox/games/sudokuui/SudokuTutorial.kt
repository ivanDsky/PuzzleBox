package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.TutorialStep
import java.time.LocalDate

/** Tutorial refs are custom boards: a fixed day and no puzzle number (A11). */
private val TUTORIAL_DAY: LocalDate = LocalDate.of(2026, 1, 1)

/**
 * One board size's three lessons, all cut from one solved grid (row-major digits). Squares
 * are numbered `row * size + col`. Each lesson's first square is where the hint button
 * points first, so the copy and the hint agree on the one next move.
 *
 * - [lastDigitSquare]: lesson 1's only open square.
 * - [boxSquares]: lesson 2's open squares, a block inside one box. Every row, column and box
 *   keeps 0 or at least 2 of them open, so "fill the last empty square" never applies there.
 *   The first square holds the box's lowest missing digit, which fits nowhere else in the box.
 * - [notesSquares]: lesson 3's two open squares, sharing a row and a box, so both of the
 *   row's missing digits look possible in each. The first square holds the smaller digit,
 *   and its column already shows the larger one.
 */
private class LessonBoards(
    val solution: String,
    val lastDigitSquare: Int,
    val boxSquares: List<Int>,
    val notesSquares: List<Int>,
)

/** LinkedIn Mini Sudoku #450 and the Wikimedia sample sudoku's solution. */
private val BOARDS: Map<Int, LessonBoards> = mapOf(
    6 to LessonBoards(
        solution = "463251521436142563356142234615615324",
        lastDigitSquare = 3,
        boxSquares = listOf(21, 15, 17, 23),
        notesSquares = listOf(24, 25),
    ),
    9 to LessonBoards(
        solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179",
        lastDigitSquare = 14,
        boxSquares = listOf(32, 30, 31, 39, 40, 41),
        notesSquares = listOf(66, 68),
    ),
)

/** The three lessons (spec A18) on [definition]'s own board size: last digit, box, notes. */
fun sudokuTutorial(definition: SudokuDefinition): List<TutorialStep> {
    val size = definition.variant.geometry.size
    val boards = requireNotNull(BOARDS[size]) { "no tutorial board for size $size" }
    val solution = boards.solution.map { it.digitToInt() }
    val boxDigit = solution[boards.boxSquares.first()]
    return listOf(
        lesson(
            definition,
            "Each row needs every number once. Fill the empty square.",
            solution,
            open = listOf(boards.lastDigitSquare),
        ),
        lesson(
            definition,
            "Each box needs every number once, too. Find the one empty square in the box where the $boxDigit fits, then fill the rest.",
            solution,
            open = boards.boxSquares,
        ),
        lesson(
            definition,
            "Notes are small marks for numbers that might fit. Turn Notes on and mark the two numbers " +
                "this row needs in its first empty square. Then turn Notes off and place the one its column needs.",
            solution,
            open = boards.notesSquares,
        ),
    )
}

private fun lesson(definition: SudokuDefinition, instruction: String, solution: List<Int>, open: List<Int>): TutorialStep {
    val puzzle = SudokuPuzzle(
        variant = definition.variant,
        givens = solution.mapIndexed { idx, digit -> if (idx in open) 0 else digit },
        solution = solution,
    )
    return TutorialStep(
        instruction = instruction,
        puzzle = Puzzle(definition.id, definition.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle)),
        ref = SudokuPuzzleRef(definition.id, TUTORIAL_DAY, null),
    )
}
