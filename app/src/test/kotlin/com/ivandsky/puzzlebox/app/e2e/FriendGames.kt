package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudokuui.SudokuPlayController
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot

/**
 * A game the friend open loop runs over: its editor [flags] (none once released), the one-solution [board] its author
 * publishes, and how a friend plays that board to its win through the board's own input (the
 * handlers its gestures call), never by writing a result.
 */
abstract class FriendGame(private val label: String, val flags: List<ReleaseFlag> = emptyList()) {
    abstract val board: EditorDraft<*>
    val id: GameId get() = board.gameId

    /** Plays [slot]'s open board to its win; the win's result is left to the caller to await. */
    abstract fun ShellRobot.solve(slot: PlaySlot.Custom)

    override fun toString() = label
}

/** A 5×5 Zip board whose walls force one path: a corridor snaking row by row. */
object ZipCorridor : FriendGame("Zip") {
    private const val SIZE = 5

    /** The one path, row-major cell indices. */
    private val PATH = listOf(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14, 19, 18, 17, 16, 15, 20, 21, 22, 23, 24)

    /** Walls under these cells force [PATH]; the waypoints number its ends and middle. */
    private val WALLED = listOf(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19)

    override val board = EditorDraft(
        ZipEditor,
        DraftState(ZipDraft(n = SIZE, walls = WALLED.map { Wall(Cell(it), WallDirection.DOWN) }.toSet(), path = PATH, waypoints = listOf(0, 12, 24))),
    )

    override fun ShellRobot.solve(slot: PlaySlot.Custom) {
        val controller = awaitController<ZipPlayController>(slot) { it.session != null }
        rule.runOnIdle { PATH.forEach { controller.onDragEvent(DragEvent.Extend(GridCell(it / SIZE, it % SIZE))) } }
    }
}

/** A unique 6×6 Tango board: ten givens and four edge markers. */
object TangoSunMoon : FriendGame("Tango") {
    /** The board's full solution, row-major. */
    private val SOLUTION: List<TangoCell> = listOf("SSMSMM", "SSMSMM", "MMSMSS", "SSMMSM", "MMSSMS", "MMSMSS")
        .flatMap { row -> row.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } }

    /** The cells the draft gives; the friend places the rest. */
    private val GIVENS = setOf(0, 1, 6, 7, 14, 16, 22, 29, 32, 33)

    private val MARKERS = listOf(
        TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
        TangoMarker(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
        TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
    )

    override val board = EditorDraft(TangoEditor, DraftState(TangoDraft(6, GIVENS.associateWith { SOLUTION[it] }, MARKERS)))

    override fun ShellRobot.solve(slot: PlaySlot.Custom) {
        val controller = awaitController<TangoPlayController>(slot) { it.session != null }
        rule.runOnIdle {
            SOLUTION.forEachIndexed { idx, cell -> if (idx !in GIVENS) controller.onPlacement(TangoPlacement(idx, cell)) }
        }
    }
}

/** A 6×6 Mini Sudoku with one solution the hint ladder finishes. */
object MiniSudokuCorner : FriendGame("MiniSudoku") {
    private val MINI = SudokuGames.MINI

    /** `0` is an empty cell. */
    private val GIVENS = grid("....62..643..2.5.3..1....3...4.65.2.")
    private val SOLUTION = grid("543162216435624513351246132654465321")

    private fun grid(text: String): List<Int> = text.map { if (it == '.') 0 else it.digitToInt() }

    override val board = EditorDraft(SudokuEditor(MINI), DraftState(SudokuDraft(MINI.variant.geometry.size, GIVENS)))

    override fun ShellRobot.solve(slot: PlaySlot.Custom) {
        val board = checkNotNull(awaitController<SudokuPlayController>(slot) { it.board != null }.board)
        // One entry per frame: the motion reads each commit against the last, and only the
        // single last entry of a solve is the win it hands off on.
        GIVENS.forEachIndexed { cell, given ->
            if (given == 0) {
                rule.runOnIdle {
                    board.select(cell)
                    board.input(SOLUTION[cell])
                }
                rule.waitForIdle()
            }
        }
    }
}
