package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec

/**
 * The boards the trainer tests walk, copied from games:sudoku's test corpus (whose test resources sudoku-ui cannot
 * reach) into `fixtures/mini-trainer-boards.txt`, one `id givens solution` line each. Real dailies hold singles only
 * (li:1, li:227, li:454, li:111: every id but the eliminations, li:111 being the one that reaches naked single); the
 * generated boards reach the four eliminations: rnd:517 a
 * claiming and a naked pair, rnd:1411 three pointings and a claiming, rnd:1964 a pointing and the corpus's one
 * hidden pair. No fixture reaches a triple (the Mini stepper offers none).
 */
internal object SudokuTrainerFixtures {

    val trainer = SudokuTrainer()

    private val puzzles: Map<String, SudokuPuzzle> by lazy {
        val text = checkNotNull(SudokuTrainerFixtures::class.java.getResourceAsStream("/fixtures/mini-trainer-boards.txt")) {
            "missing fixtures/mini-trainer-boards.txt"
        }.bufferedReader().use { it.readText() }
        text.lines().filter { it.isNotBlank() }.associate { line ->
            val (id, givens, solution) = line.split(' ')
            id to SudokuPuzzle(SudokuVariants.MINI, grid(givens), grid(solution))
        }
    }

    /** The real dailies, singles only. */
    val dailies: Map<String, SudokuPuzzle> by lazy { puzzles.filterKeys { it.startsWith("li:") } }

    /** The generated boards that reach the four eliminations. */
    val eliminating: Map<String, SudokuPuzzle> by lazy { puzzles.filterKeys { it.startsWith("rnd:") } }

    val all: Map<String, SudokuPuzzle> get() = puzzles

    /** [puzzle] as the app's canonical [Puzzle], the way a trainer is handed one. */
    fun canonical(puzzle: SudokuPuzzle): Puzzle = (SudokuGames.MINI.decode(SudokuCodec.encode(puzzle)) as DecodeResult.Ok).value

    private fun grid(text: String): List<Int> = text.map { if (it == '.') 0 else it.digitToInt() }
}
