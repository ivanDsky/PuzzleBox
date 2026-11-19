package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The boards the trainer tests walk, from the vendored corpus (see `SudokuCorpusTest`). Real dailies hold
 * singles only; the generated boards are the ones that reach an elimination: rnd:517 a claiming and a
 * naked pair, rnd:1411 three pointings and a claiming, rnd:1964 a pointing and the corpus's one hidden pair.
 * No fixture reaches a triple (the Mini stepper offers none).
 */
internal object SudokuTrainerFixtures {

    @Serializable
    private data class Record(val id: String, val givens: String, val solution: String, val n: Int = 0, val date: String = "")

    private val real: Map<String, Record> by lazy { records("sudoku-boards.json") }
    private val generated: Map<String, Record> by lazy { records("sudoku-generated.json") }

    /** Three real dailies. */
    val dailies: Map<String, SudokuPuzzle> by lazy { listOf("li:1", "li:227", "li:454").associateWith { puzzle(real.getValue(it)) } }

    /** The generated boards that reach the four eliminations. */
    val eliminating: Map<String, SudokuPuzzle> by lazy {
        listOf("rnd:517", "rnd:1411", "rnd:1964").associateWith { puzzle(generated.getValue(it)) }
    }

    val all: Map<String, SudokuPuzzle> by lazy { dailies + eliminating }

    private fun puzzle(r: Record) = SudokuPuzzle(SudokuVariants.MINI, KnownSolutions.grid(r.givens), KnownSolutions.grid(r.solution))

    private fun records(file: String): Map<String, Record> =
        Json.decodeFromString<List<Record>>(KnownSolutions.fixture(file)).associateBy { it.id }
}
