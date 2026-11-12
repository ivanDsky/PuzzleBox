package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.AutoCross
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Clear
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Cross
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Erase
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Hint
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Place
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Redo
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Remove
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Undo
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Queens controller records what it applies: two fixed scripts run through the real
 * controller on a fake clock, and the log, the replay and the completion are read back.
 */
class QueensReplayRecordingTest {

    private var fakeNow: Instant = START

    /** One play: the real controller with its settings, its log and what it handed to the play feature. */
    private inner class Play(val log: MoveLog, autoCross: Boolean) {
        val saved = ArrayList<ByteArray>()
        val won = ArrayList<CompletionRecord>()
        val settings = InMemoryQueensSettings(autoCross)
        val controller = QueensPlayController(
            env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }, log),
            settings = settings,
            now = { fakeNow },
            hintMode = QueensPlayFixtures.hintMode,
        )
        val board: QueensBoardState get() = controller.board!!
        val session: QueensSession get() = board.session

        /** Runs [script] from play time [fromMs]; [afterEach] sees the play right after every input. */
        fun run(script: List<Input>, fromMs: Long = 0L, afterEach: (Input) -> Unit = {}) {
            var playMs = fromMs
            for (input in script) {
                fakeNow = fakeNow.plusMillis(input.awayMs + input.atMs - playMs)
                playMs = input.atMs
                input.act(this)
                afterEach(input)
            }
        }
    }

    /**
     * One scripted input. It lands at play time [atMs], after [awayMs] more of wall time that is not
     * play. [move] is what the log must hold for it (null = nothing). [today] is the reducer call
     * the controller made for it before it recorded anything, with the wall-clock instant it passed.
     */
    private class Input(
        val atMs: Long,
        val move: QueensReplayMove?,
        val awayMs: Long = 0L,
        val today: QueensSession.(Instant) -> QueensSession = { this },
        val act: Play.() -> Unit,
    )

    private fun fresh(log: MoveLog = MoveLog.off(), autoCross: Boolean = true): Play {
        fakeNow = START
        val play = Play(log, autoCross)
        assertEquals(Adopt.Adopted, play.controller.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()))
        return play
    }

    //region scripts

    private fun cross(atMs: Long, idx: Int) =
        Input(atMs, Cross(Cell(idx)), today = { withCrossToggled(Cell(idx), it) }) { board.onTap(idx) }

    private fun queen(atMs: Long, idx: Int) =
        Input(atMs, Place(Cell(idx)), today = { withQueenPlaced(Cell(idx), it) }) { board.onTap(idx) }

    private fun remove(atMs: Long, idx: Int) =
        Input(atMs, Remove(Cell(idx)), today = { withQueenRemoved(Cell(idx), it) }) { board.onTap(idx) }

    /** The last queen: the win folds the running window into the banked time, [atMs] of it. */
    private fun winningQueen(atMs: Long, idx: Int) =
        Input(atMs, Place(Cell(idx)), today = { withQueenPlaced(Cell(idx), it).copy(elapsedMs = atMs) }) { board.onTap(idx) }

    private fun ignored(atMs: Long, act: Play.() -> Unit) = Input(atMs, move = null, act = act)

    private fun background(atMs: Long) =
        Input(atMs, move = null, today = { copy(elapsedMs = atMs) }) { controller.onBackground() }

    private fun foreground(atMs: Long, awayMs: Long) = Input(atMs, move = null, awayMs = awayMs) { controller.onForeground() }

    /** Two taps per cell: markless → X → queen. */
    private fun queens(fromMs: Long, vararg cells: Int): List<Input> =
        cells.flatMapIndexed { i, idx -> listOf(cross(fromMs + 200L * i, idx), queen(fromMs + 200L * i + 100L, idx)) }

    /** Auto-cross ON. Every kind of board action, a counted hint and a free one, then the input nothing records. */
    private val openingHalf: List<Input> = listOf(
        cross(1_000L, 0),
        queen(1_500L, 0),
        remove(1_900L, 0),
        ignored(2_200L) { board.onTap(64) }, // off the board
        Input(2_400L, Hint, today = { copy(hintsUsed = 1) }) { controller.requestHint() },
        ignored(2_500L) { controller.requestHint() }, // the same step again is free
        Input(3_000L, Cross(Cell(1)), today = { withCrossToggled(Cell(1), it) }) { board.onDragStart(1) },
        Input(3_010L, Cross(Cell(2)), today = { withCrossToggled(Cell(2), it) }) { board.onDragCell(2) },
        ignored(3_015L) { board.onDragCell(2) }, // already crossed
        Input(3_020L, Cross(Cell(3)), today = { withCrossToggled(Cell(3), it) }) {
            board.onDragCell(3)
            board.onDragEnd()
        },
        Input(3_500L, Erase(Cell(1), extend = false), today = { withCrossErased(Cell(1), false, it) }) { board.onDragStart(1) },
        Input(3_510L, Erase(Cell(2), extend = true), today = { withCrossErased(Cell(2), true, it) }) { board.onDragCell(2) },
        ignored(3_520L) {
            board.onDragCell(4) // no cross to erase
            board.onDragEnd()
        },
        Input(4_000L, Undo, today = { withUndo() }) { controller.undo() },
        Input(4_200L, Redo, today = { withRedo() }) { controller.redo() },
        ignored(4_300L) { controller.redo() }, // nothing left to redo
    )

    /** A minute away from the app at play time 4.5 s. */
    private val pause: List<Input> = listOf(background(4_500L), foreground(4_500L, awayMs = 60_000L))

    /** Both Auto-cross flips from the menu, Clear and its undo, then the solve. Ends at 8 s with one hint. */
    private val closingHalf: List<Input> = listOf(
        Input(5_000L, AutoCross(false), today = { withAutoCross(false) }) { controller.chooseAutoCross(false) },
        cross(5_500L, 12),
        queen(5_600L, 12),
        Input(6_000L, AutoCross(true), today = { withAutoCross(true) }) { controller.chooseAutoCross(true) },
        Input(6_500L, Clear, today = { withCleared(it) }) { board.clearBoard() },
        Input(6_600L, Undo, today = { withUndo() }) { controller.undo() },
    ) + queens(6_700L, 0, 23, 29, 34, 46, 49) + listOf(cross(7_900L, 59), winningQueen(8_000L, 59))

    private val menuScript: List<Input> = openingHalf + pause + closingHalf

    /**
     * Auto-cross OFF from the start, switched on in Settings while the app is away, so the flip
     * lands at the banked play time. Ends at 2.8 s with no hint.
     */
    private val settingsScript: List<Input> = listOf(
        cross(500L, 0),
        queen(600L, 0),
        background(1_000L),
        Input(1_000L, AutoCross(true), awayMs = 30_000L, today = { withAutoCross(true) }) { settings.stored.value = true },
        foreground(1_000L, awayMs = 30_000L),
        ignored(1_100L) { controller.chooseAutoCross(true) }, // the value it already has
        remove(1_200L, 0),
    ) + queens(1_300L, 0, 12, 23, 29, 34, 46, 49) + listOf(cross(2_700L, 59), winningQueen(2_800L, 59))

    //endregion

    /**
     * Characterization: written against the controller that called the session reducers itself,
     * and green there. Routing every change through the move log must not shift one session.
     */
    @Test
    fun `the controller stores what today's reducer calls give, input for input`() {
        for ((autoCross, script) in listOf(true to menuScript, false to settingsScript)) {
            for (log in listOf(MoveLog.off(), MoveLog.fresh(QueensDefinition.id))) {
                val play = fresh(log, autoCross)
                var expected = QueensSession(QueensPlayFixtures.ref(), QueensPlayFixtures.puzzle(), autoCross = autoCross)
                assertEquals(expected, play.session)

                play.run(script) { input ->
                    expected = input.today(expected, fakeNow)
                    assertEquals("after the input at ${input.atMs} ms", expected, play.session)
                }

                assertEquals(QueensPlayFixtures.solution.toSet(), play.session.queens.toSet())
                assertEquals(Instant.parse(if (autoCross) "2026-11-02T00:01:08Z" else "2026-11-02T00:01:02.800Z"), play.session.completedAt)
                assertEquals(if (autoCross) 8_000L else 2_800L, play.won.single().time)
                assertEquals(if (autoCross) 1 else 0, play.won.single().hints)
            }
        }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-11-02T00:00:00Z")
    }
}
