package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHints
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ticket 15 (spec A18, S9): each Sudoku game teaches three lessons on its own board size,
 * cut from a known solution. Every lesson decodes, agrees with its solution, has exactly
 * the open squares documented here, solves one way only, and the hint button's first move
 * is the lesson's intended one.
 */
class SudokuTutorialTest {

    /** One size's documented lessons: open squares in intended order (the first is the target). */
    private class Expected(val definition: SudokuDefinition, val solution: String, val open: List<List<Int>>)

    private val expected = listOf(
        Expected(
            SudokuGames.MINI,
            solution = "463251521436142563356142234615615324",
            open = listOf(listOf(3), listOf(21, 15, 17, 23), listOf(24, 25)),
        ),
        Expected(
            SudokuGames.CLASSIC,
            solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179",
            open = listOf(listOf(14), listOf(32, 30, 31, 39, 40, 41), listOf(66, 68)),
        ),
    )

    private val epoch = Instant.EPOCH

    private fun puzzleOf(definition: SudokuDefinition, step: TutorialStep): SudokuPuzzle =
        (definition.decodePuzzle(step.puzzle.payload) as DecodeResult.Ok).value

    private fun openSquares(puzzle: SudokuPuzzle): List<Int> = puzzle.givens.indices.filter { puzzle.givens[it] == 0 }

    /** The digits [unit] still needs: its solution digits on open squares. */
    private fun missing(puzzle: SudokuPuzzle, unit: SudokuUnit): Set<Int> =
        puzzle.geometry.cellsOf(unit).filter { puzzle.givens[it] == 0 }.map { puzzle.solution[it] }.toSet()

    private fun unitOf(puzzle: SudokuPuzzle, kind: UnitKind, idx: Int): SudokuUnit {
        val geometry = puzzle.geometry
        val index = when (kind) {
            UnitKind.ROW -> geometry.rowOf(idx)
            UnitKind.COLUMN -> geometry.colOf(idx)
            UnitKind.BOX -> geometry.boxOf(idx)
        }
        return SudokuUnit(kind, index)
    }

    private fun forEachLesson(check: (Expected, Int, TutorialStep, SudokuPuzzle) -> Unit) {
        for (game in expected) {
            val steps = sudokuTutorial(game.definition)
            for ((lesson, step) in steps.withIndex()) check(game, lesson, step, puzzleOf(game.definition, step))
        }
    }

    @Test
    fun `each game ships three lessons through its play registration`() {
        for (game in expected) {
            val tutorial = SudokuPlayUi(game.definition).tutorial
            assertEquals(3, tutorial.size)
            assertEquals(sudokuTutorial(game.definition).map { it.instruction }, tutorial.map { it.instruction })
        }
    }

    @Test
    fun `every lesson decodes on its own board size as a custom board`() {
        forEachLesson { game, _, step, puzzle ->
            assertEquals(game.definition.id, step.puzzle.gameId)
            assertEquals(game.definition.variant.id, step.puzzle.variant)
            assertTrue(game.definition.decode(step.puzzle.payload) is DecodeResult.Ok)
            assertEquals(game.definition.variant, puzzle.variant)
            assertEquals(SudokuPuzzleRef(game.definition.id, PuzzleKey.Daily(LocalDate.of(2026, 1, 1), null)), step.ref)
            assertFalse(step.guided)
        }
    }

    @Test
    fun `every lesson is its known solution with exactly the documented squares open`() {
        forEachLesson { game, lesson, step, puzzle ->
            assertEquals(step.instruction, game.solution.map { it.digitToInt() }, puzzle.solution)
            assertEquals(step.instruction, game.open[lesson].sorted(), openSquares(puzzle))
            for (idx in puzzle.givens.indices) {
                if (puzzle.givens[idx] != 0) assertEquals(step.instruction, puzzle.solution[idx], puzzle.givens[idx])
            }
        }
    }

    @Test
    fun `every lesson solves one way only, to its solution`() {
        forEachLesson { _, _, step, puzzle ->
            val result = SudokuSolver.solve(puzzle.geometry, puzzle.givens)
            assertEquals(step.instruction, 1, result.count)
            assertEquals(step.instruction, puzzle.solution, result.solution)
        }
    }

    @Test
    fun `the hint button's first move is each lesson's intended one`() {
        forEachLesson { game, lesson, step, puzzle ->
            val target = game.open[lesson].first()
            val hint = SudokuHints().hint(puzzle, SudokuBoard.of(puzzle)) as SudokuHint.Place
            assertEquals(step.instruction, target, hint.cell)
            assertEquals(step.instruction, puzzle.solution[target], hint.digit)
        }
    }

    @Test
    fun `lesson one's open square is the last one in its row`() {
        for (game in expected) {
            val puzzle = puzzleOf(game.definition, sudokuTutorial(game.definition)[0])
            val target = game.open[0].single()
            assertEquals(setOf(puzzle.solution[target]), missing(puzzle, unitOf(puzzle, UnitKind.ROW, target)))
        }
    }

    @Test
    fun `lesson two needs the box, not the last empty square`() {
        for (game in expected) {
            val step = sudokuTutorial(game.definition)[1]
            val puzzle = puzzleOf(game.definition, step)
            val target = game.open[1].first()
            val digit = puzzle.solution[target]
            val box = unitOf(puzzle, UnitKind.BOX, target)

            // No row, column or box has a single empty square, so lesson one's move never applies.
            for (unit in puzzle.geometry.units) {
                assertTrue("$unit", missing(puzzle, unit).size != 1)
            }
            // Every other empty square of the box sees the digit in its row or column.
            val boxOpen = puzzle.geometry.cellsOf(box).filter { puzzle.givens[it] == 0 }
            assertEquals(game.open[1].sorted(), boxOpen)
            for (other in boxOpen - target) {
                val blocked = listOf(UnitKind.ROW, UnitKind.COLUMN).any { kind ->
                    puzzle.geometry.cellsOf(unitOf(puzzle, kind, other)).any { puzzle.givens[it] == digit }
                }
                assertTrue("square $other", blocked)
            }
            val hint = SudokuHints().hint(puzzle, SudokuBoard.of(puzzle)) as SudokuHint.Place
            assertEquals(SudokuTechnique.HIDDEN_SINGLE_BOX, hint.technique)
            assertEquals(box, hint.unit)
            // The step completes only once the whole box is filled, so the copy says so (E1).
            assertEquals(
                "Each box needs every number once, too. Find the one empty square in the box where the $digit fits, then fill the rest.",
                step.instruction,
            )
        }
    }

    @Test
    fun `lesson three's row offers two numbers and the column picks one`() {
        for (game in expected) {
            val puzzle = puzzleOf(game.definition, sudokuTutorial(game.definition)[2])
            val (first, second) = game.open[2]
            val row = unitOf(puzzle, UnitKind.ROW, first)
            assertEquals(row, unitOf(puzzle, UnitKind.ROW, second))
            assertEquals(unitOf(puzzle, UnitKind.BOX, first), unitOf(puzzle, UnitKind.BOX, second))

            val notes = missing(puzzle, row)
            assertEquals(setOf(puzzle.solution[first], puzzle.solution[second]), notes)
            assertEquals(notes, missing(puzzle, unitOf(puzzle, UnitKind.BOX, first)))
            assertEquals(setOf(puzzle.solution[first]), missing(puzzle, unitOf(puzzle, UnitKind.COLUMN, first)))
        }
    }

    @Test
    fun `lesson three plays as notes, then place`() {
        for (game in expected) {
            val puzzle = puzzleOf(game.definition, sudokuTutorial(game.definition)[2])
            val (first, second) = game.open[2]
            val (answer, other) = puzzle.solution[first] to puzzle.solution[second]
            var session = SudokuSession(ref = null, puzzle = puzzle).withNotesMode(true)

            for (digit in listOf(answer, other)) {
                session = session.withCommand(move(SudokuMoves.toggleNote(session.board, first, digit)), epoch)
            }
            val bothNotes = (1 shl (answer - 1)) or (1 shl (other - 1))
            assertEquals(SudokuCell.Notes(bothNotes), session.board.cells[first])

            session = session.withNotesMode(false)
            session = session.withCommand(move(SudokuMoves.place(session.board, first, answer)), epoch)
            assertFalse(session.isComplete)
            session = session.withCommand(move(SudokuMoves.place(session.board, second, other)), epoch)
            assertTrue(session.isComplete)
        }
    }

    @Test
    fun `every lesson is solved by placing its answers`() {
        forEachLesson { game, lesson, step, puzzle ->
            var session = SudokuSession(ref = null, puzzle = puzzle)
            for (idx in game.open[lesson]) {
                assertFalse(step.instruction, session.isComplete)
                session = session.withCommand(move(SudokuMoves.place(session.board, idx, puzzle.solution[idx])), epoch)
            }
            assertTrue(step.instruction, session.isComplete)
        }
    }

    @Test
    fun `the play controller adopts every lesson`() {
        val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {})
        for (game in expected) {
            val ui = SudokuPlayUi(game.definition)
            for (step in ui.tutorial) {
                assertEquals(step.instruction, Adopt.Adopted, ui.createController(env).adoptToday(step.puzzle, step.ref))
            }
        }
    }

    @Test
    fun `the copy is plain and free of solver words`() {
        val jargon = listOf("single", "hidden", "candidate", "unit", "naked", "peer", "digit")
        forEachLesson { _, _, step, _ ->
            val copy = step.instruction.lowercase()
            for (word in jargon) assertFalse("'$word' in: ${step.instruction}", copy.contains(word))
        }
    }

    /** The command a lesson's intended input builds; null would mean the input is refused. */
    private fun <T : Any> move(command: T?): T = requireNotNull(command) { "the lesson's move was refused" }
}
