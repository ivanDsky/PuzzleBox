package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.play.MiniSudokuPlayFixture
import com.ivandsky.puzzlebox.app.play.StrandsPlayFixture
import com.ivandsky.puzzlebox.app.play.TangoPlayFixture
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.games.queens.Cell as QueensCell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudokuui.SudokuPlayController
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot

/**
 * One of the five dailies and the moves that win its fixture board ([E2eKoinGraph] serves it fresh), each through
 * the board's own input (the handlers its gestures call), never by writing a result. The last move is the winning
 * one, so a test can hold the clock before it and watch what the win does frame by frame.
 */
internal enum class DailySolve(val id: GameId) {
    Zip(ZipDefinition.id) {
        override fun ShellRobot.moves(slot: PlaySlot): List<() -> Unit> {
            val zip = awaitController<ZipPlayController>(slot) { it.session != null }
            return ZipPlayFixture.solution.map { cell -> { zip.onDragEvent(DragEvent.Extend(GridCell(cell.idx / ZIP_SIZE, cell.idx % ZIP_SIZE))) } }
        }
    },
    Tango(TangoDefinition.id) {
        override fun ShellRobot.moves(slot: PlaySlot): List<() -> Unit> {
            val tango = awaitController<TangoPlayController>(slot) { it.session != null }
            return TangoPlayFixture.placements.map { (idx, cell) -> { tango.onPlacement(TangoPlacement(idx, cell)) } }
        }
    },
    Queens(QueensDefinition.id) {
        override fun ShellRobot.moves(slot: PlaySlot): List<() -> Unit> {
            val board = checkNotNull(awaitController<QueensPlayController>(slot) { it.board != null }.board)
            // A cell cycles blank, X, queen; an auto-cross may skip the X, so tap until the queen stands.
            return QUEENS_SOLUTION.map { cell -> { while (QueensCell(cell) !in board.session.queens) board.onTap(cell) } }
        }
    },
    MiniSudoku(SudokuGames.MINI.id) {
        override fun ShellRobot.moves(slot: PlaySlot): List<() -> Unit> {
            val board = checkNotNull(awaitController<SudokuPlayController>(slot) { it.board != null }.board)
            val puzzle = MiniSudokuPlayFixture.puzzle
            return puzzle.givens.indices.filter { puzzle.givens[it] == 0 }.map { cell ->
                {
                    board.select(cell)
                    board.input(puzzle.solution[cell])
                }
            }
        }
    },
    Strands(StrandsDefinition.id) {
        override fun ShellRobot.moves(slot: PlaySlot): List<() -> Unit> {
            val strands = awaitController<StrandsPlayController>(slot) { it.session != null }
            return StrandsPlayFixture.rows.mapIndexed { row, word -> { strands.onTrace(TraceStep.Submit(word.indices.map { Coord(row, it) })) } }
        }
    };

    /** The winning moves of [slot]'s live board, in order. */
    protected abstract fun ShellRobot.moves(slot: PlaySlot): List<() -> Unit>

    /**
     * Opens today's board from the hub and plays every move but the last, one a frame (a board's motion reads each
     * commit against the one before). Returns the winning move, still to play.
     */
    fun ShellRobot.playToTheLastMove(): () -> Unit {
        openGame(id.value)
        val moves = moves(PlaySlot.Daily(id, E2E_TODAY))
        moves.dropLast(1).forEach { move ->
            rule.runOnIdle(move)
            rule.waitForIdle()
        }
        return moves.last()
    }

    private companion object {
        const val ZIP_SIZE = 5

        /** A solution of the 4×4 fixture (rows are its regions); the fixture's own listed one has touching queens. */
        val QUEENS_SOLUTION = listOf(1, 7, 8, 14)
    }
}
