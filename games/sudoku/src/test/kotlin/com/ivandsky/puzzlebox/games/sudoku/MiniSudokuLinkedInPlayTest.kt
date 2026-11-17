package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.linkedin.LinkedInVoyagerCell
import com.ivandsky.puzzlebox.game.replay.MoveKind
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Mini Sudoku LinkedIn encoder against the Mini Sudoku #463 daily captures (puzzle from the game read, boards from the saves). */
class MiniSudokuLinkedInPlayTest {

    private val served: JsonObject = json("minisudoku-voyager-game-daily-unplayed.response.json")
        .getValue("included").jsonArray.first().jsonObject
        .getValue("gamePuzzle").jsonObject.getValue("miniSudokuGamePuzzle").jsonObject
    private val solution: List<Int> = served.getValue("solution").jsonArray.map { it.jsonPrimitive.int }
    private val presets: Set<Int> = served.getValue("presetCellIdxes").jsonArray.map { it.jsonPrimitive.int }.toSet()
    private val puzzle = SudokuPuzzle(SudokuVariants.MINI, solution.mapIndexed { idx, digit -> if (idx in presets) digit else 0 }, solution)

    private val capturedWin: List<LinkedInVoyagerCell> = savedCells("minisudoku-voyager-save-daily-win.request.json")
    private val capturedOpening: List<LinkedInVoyagerCell> = savedCells("minisudoku-voyager-save-daily-opening.request.json")

    /** The cells the capture filled, its first save's cell first. */
    private val capturedSteps: List<Step> =
        (capturedOpening + (capturedWin - capturedOpening.toSet())).map { Step.Place(it.idx, it.value) }

    private fun json(name: String): JsonObject = Json.parseToJsonElement(KnownSolutions.fixture(name)).jsonObject

    private fun savedCells(name: String): List<LinkedInVoyagerCell> =
        json(name).getValue("variables").jsonObject.getValue("entity").jsonObject.getValue("entity").jsonObject
            .getValue("gameStoredRecord").jsonObject.getValue("gameStateUnion").jsonObject
            .getValue("miniSudokuGameState").jsonArray.map { it.jsonObject }
            .map { cell ->
                LinkedInVoyagerCell(
                    idx = cell.getValue("cellIdx").jsonPrimitive.int,
                    value = cell.getValue("cellContentUnion").jsonObject.getValue("cellValue").jsonPrimitive.int,
                )
            }

    private sealed interface Step {
        data class Place(val idx: Int, val digit: Int) : Step

        data class Note(val idx: Int, val digit: Int) : Step
    }

    /** The session after each step, through the reducer live play uses: the board that solves it is stamped complete. */
    private fun boards(steps: List<Step>): List<SudokuSession> {
        var session = SudokuSession(ref = null, puzzle = puzzle)
        return steps.map { step ->
            val command = when (step) {
                is Step.Place -> SudokuMoves.place(session.board, step.idx, step.digit)
                is Step.Note -> SudokuMoves.toggleNote(session.board, step.idx, step.digit)
            }
            session = session.withCommand(checkNotNull(command), Instant.EPOCH)
            session
        }
    }

    private fun solve(
        steps: List<Step> = capturedSteps,
        kinds: List<MoveKind> = steps.map { MoveKind.Normal },
        firstMoveMs: Long = FIRST_MOVE_MS,
    ) = LinkedInSolve(boards(steps), kinds, steps.indices.map { firstMoveMs + it * 1_000L })

    @Test
    fun `a solve of the captured puzzle encodes the captured win board for the voyager save`() {
        val play = checkNotNull(MiniSudokuLinkedInPlay.encode(solve()))

        val voyager = checkNotNull(play.voyager)
        assertEquals(capturedWin, voyager.cells)
        assertEquals("miniSudokuGameState", voyager.stateName)
        assertTrue("a voyager play carries no updateGameState states", play.states.isEmpty() && play.payloadKeys.isEmpty())
        assertNull(play.opening)
        assertTrue(play.mistakeFree)
    }

    @Test
    fun `the cells are sent by ascending index whatever order they were placed in`() {
        val play = checkNotNull(MiniSudokuLinkedInPlay.encode(solve(capturedSteps.reversed())))

        assertEquals(capturedWin, play.voyager?.cells)
    }

    @Test
    fun `the opening is the first placed cell alone, at the time it was placed`() {
        val voyager = checkNotNull(MiniSudokuLinkedInPlay.encode(solve())?.voyager)

        assertEquals(capturedOpening, voyager.openingCells)
        assertEquals(FIRST_MOVE_MS, voyager.openingTimeMs)
    }

    @Test
    fun `notes before the first digit are not the opening`() {
        val open = capturedWin.first().idx
        val steps = listOf(Step.Note(open, 1), Step.Note(open, 2)) + capturedSteps

        val voyager = checkNotNull(MiniSudokuLinkedInPlay.encode(solve(steps))?.voyager)

        assertEquals(capturedOpening, voyager.openingCells)
        assertEquals("the tick of the first digit, after two note ticks", FIRST_MOVE_MS + 2_000L, voyager.openingTimeMs)
        assertEquals(capturedWin, voyager.cells)
    }

    @Test
    fun `an opening placed at time zero is sent as one millisecond`() {
        val voyager = checkNotNull(MiniSudokuLinkedInPlay.encode(solve(firstMoveMs = 0))?.voyager)

        assertEquals(1L, voyager.openingTimeMs)
    }

    @Test
    fun `preset cells are never sent`() {
        val voyager = checkNotNull(MiniSudokuLinkedInPlay.encode(solve())?.voyager)

        assertEquals(emptySet<Int>(), (voyager.cells + voyager.openingCells).map { it.idx }.toSet() intersect presets)
        assertEquals(puzzle.geometry.cellCount - presets.size, voyager.cells.size)
    }

    @Test
    fun `an unsolved final board encodes nothing`() {
        assertNull(MiniSudokuLinkedInPlay.encode(solve(capturedSteps.dropLast(1))))
    }

    @Test
    fun `a mistake, a hint or an undo anywhere in the solve is not mistake-free`() {
        listOf(MoveKind.Mistake, MoveKind.Hint, MoveKind.Undo).forEach { flaw ->
            val kinds = capturedSteps.mapIndexed { i, _ -> if (i == 1) flaw else MoveKind.Normal }

            val play = checkNotNull(MiniSudokuLinkedInPlay.encode(solve(kinds = kinds)))

            assertFalse("$flaw", play.mistakeFree)
            assertEquals(capturedWin, play.voyager?.cells)
        }
    }

    @Test
    fun `the Mini Sudoku plugin carries the encoder and the bundled classic has none`() {
        assertNotNull(sudokuPlugin(SudokuGames.MINI).linkedInPlay)
        assertNull(sudokuPlugin(SudokuGames.CLASSIC).linkedInPlay)
    }

    private companion object {
        const val FIRST_MOVE_MS = 3_412L
    }
}
