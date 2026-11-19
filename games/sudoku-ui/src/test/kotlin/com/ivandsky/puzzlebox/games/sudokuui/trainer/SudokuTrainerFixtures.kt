package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuMoveCheck
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import kotlinx.coroutines.Dispatchers

/**
 * The boards the trainer tests walk, copied from games:sudoku's test corpus (whose test resources sudoku-ui cannot
 * reach) into `fixtures/mini-trainer-boards.txt`, one `id givens solution` line each. Real dailies hold singles only
 * (li:1, li:227, li:454: every id but the eliminations); the generated boards reach the four eliminations: rnd:517 a
 * claiming and a naked pair, rnd:1411 three pointings and a claiming, rnd:1964 a pointing and the corpus's one
 * hidden pair. No fixture reaches a triple (the Mini stepper offers none).
 */
internal object SudokuTrainerFixtures {

    val trainer = SudokuTrainer(Dispatchers.Default)

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

    /** Up to [perTechnique] snippets of every technique in the catalog, from every fixture board, in catalog order. */
    fun snippets(perTechnique: Int = 3): List<SudokuSnippet> = trainer.catalog().flatMap { technique ->
        all.values.flatMap { trainer.buildSnippets(canonical(it), technique.id, perTechnique) }
    }.map { it as SudokuSnippet }

    /** The cells of [snippet] still empty in the cut, where the player may act. */
    fun emptyCells(snippet: SudokuSnippet): List<Int> =
        (0 until snippet.puzzle.geometry.cellCount).filter { snippet.cut.boardBefore.digitAt(it) == 0 }

    /**
     * A move on [snippet]'s board the move check judges [verdict] (a digit for a single, a shown note to strike for an
     * elimination), or null when the board has none.
     */
    fun moveFor(snippet: SudokuSnippet, verdict: TrainerVerdict): SudokuTrainerMove? {
        val state = SudokuSnippetBoardState(snippet)
        for (idx in emptyCells(snippet)) {
            for (digit in state.keysAt(idx)) {
                val move = if (snippet.placesDigit) SudokuTrainerMove.Place(idx, digit) else SudokuTrainerMove.RemoveNote(idx, digit)
                val judged = SudokuMoveCheck.classify(snippet.puzzle, snippet.cut.boardBefore, snippet.cut.struckBefore, snippet.technique, move)
                if (judged == verdict) return move
            }
        }
        return null
    }

    /** The cell a [move] is on. */
    fun cellOf(move: SudokuTrainerMove): Int = when (move) {
        is SudokuTrainerMove.Place -> move.idx
        is SudokuTrainerMove.RemoveNote -> move.idx
    }

    /** The digit a [move] carries. */
    fun digitOf(move: SudokuTrainerMove): Int = when (move) {
        is SudokuTrainerMove.Place -> move.digit
        is SudokuTrainerMove.RemoveNote -> move.digit
    }

    private fun grid(text: String): List<Int> = text.map { if (it == '.') 0 else it.digitToInt() }
}
