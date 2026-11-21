package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
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

    /** Four real dailies; li:111 is the one whose easiest-first walk reaches a point where naked single is the lowest rung. */
    val dailies: Map<String, SudokuPuzzle> by lazy { listOf("li:1", "li:227", "li:454", "li:111").associateWith { puzzle(real.getValue(it)) } }

    /** The generated boards that reach the four eliminations. */
    val eliminating: Map<String, SudokuPuzzle> by lazy {
        listOf("rnd:517", "rnd:1411", "rnd:1964").associateWith { puzzle(generated.getValue(it)) }
    }

    val all: Map<String, SudokuPuzzle> by lazy { dailies + eliminating }

    /** Every real daily of the vendored corpus and every twentieth generated board: the boards the index pass and the drill set may list. */
    val corpus: Map<String, SudokuPuzzle> by lazy {
        real.mapValues { puzzle(it.value) } + generated.values.filterIndexed { n, _ -> n % 20 == 0 }.associate { it.id to puzzle(it) }
    }

    /** A practice of [kind] for [technique] on [puzzle] (the fixture [name]), starting at [board]. */
    data class PracticeStart(val name: String, val puzzle: SudokuPuzzle, val technique: TechniqueId, val kind: BoardKind, val board: SudokuBoard)

    /** Every practice of either kind the fixtures can start, in fixture, ladder, then kind order. */
    val starts: List<PracticeStart> by lazy {
        all.flatMap { (name, puzzle) ->
            SudokuTechniqueIds.all.flatMap { technique ->
                BoardKind.entries.mapNotNull { kind ->
                    SudokuPractice.start(puzzle, technique, kind)?.let { PracticeStart(name, puzzle, technique, kind, it) }
                }
            }
        }
    }

    private fun puzzle(r: Record) = SudokuPuzzle(SudokuVariants.MINI, KnownSolutions.grid(r.givens), KnownSolutions.grid(r.solution))

    private fun records(file: String): Map<String, Record> =
        Json.decodeFromString<List<Record>>(KnownSolutions.fixture(file)).associateBy { it.id }
}
