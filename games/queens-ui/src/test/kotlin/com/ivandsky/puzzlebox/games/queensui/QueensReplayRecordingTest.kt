package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
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
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** A killed play comes back: a new controller over the saved [log] snapshot and the saved session [bytes]. */
    private fun restored(log: MoveLog, bytes: ByteArray, autoCross: Boolean): Play {
        val play = Play(log, autoCross)
        assertEquals(Restored(QueensPlayFixtures.ref()), play.controller.adoptRestored(bytes))
        return play
    }

    /** The live session by play time: at [fromMs], then after the last input that landed at each time. */
    private fun Play.liveAfterEach(script: List<Input>, fromMs: Long = 0L): Map<Long, QueensSession> {
        val live = linkedMapOf(fromMs to session)
        run(script, fromMs) { live[it.atMs] = session }
        return live
    }

    /**
     * What a replay reproduces of a live session: queens, marks, undo history, hint count and the
     * setting. Not the record identity, the banked clock or the wall-clock moment of the win.
     */
    private fun QueensSession.clockFree(): QueensSession =
        copy(ref = null, elapsedMs = 0L, completedAt = completedAt?.let { Instant.EPOCH })

    private fun Play.sealed(): SolveReplay =
        checkNotNull(log.seal(ReplayOutcome.Solved, won.single().time)) { "the play sealed no replay" }

    private fun Play.recorded(): List<StampedMove> =
        checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "the play has no readable log" }.moves

    private fun timelineOf(sealed: SolveReplay): Timeline =
        checkNotNull(queensPlugin.openTimeline(QueensPlayFixtures.envelope(), sealed)) { "the sealed replay does not open" }

    private fun Timeline.sessionAt(atMs: Long): QueensSession = at(atMs).session as QueensSession

    /** S2 for one play: every live session is the replay's frame at its play time, and the replay ends solved. */
    private fun assertReplays(live: Map<Long, QueensSession>, sealed: SolveReplay) {
        val timeline = timelineOf(sealed)
        for ((atMs, session) in live) {
            assertEquals("frame at $atMs ms", session.clockFree(), timeline.sessionAt(atMs).clockFree())
        }
        val end = timeline.at(timeline.endMs)
        assertTrue(end.ended)
        assertTrue(end.facts.solved)
        assertEquals(QueensPlayFixtures.solution.toSet(), (end.session as QueensSession).queens.toSet())
    }

    /** The log a script must leave: the adopt's setting move at 0, then every input that names a move. */
    private fun recordOf(autoCross: Boolean, script: List<Input>): List<StampedMove> =
        listOf(StampedMove(0L, AutoCross(autoCross).toReplayMove())) +
            script.mapNotNull { input -> input.move?.let { StampedMove(input.atMs, it.toReplayMove()) } }

    private fun encoded(endMs: Long, moves: List<StampedMove>): ByteArray? =
        ReplayCodec.encode(ReplayRecord(QueensDefinition.id, ReplayOutcome.Solved, endMs, moves))

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

    @Test
    fun `S2 the replay shows the live session after every input and ends on the solved board`() {
        val play = fresh(MoveLog.fresh(QueensDefinition.id))
        val live = play.liveAfterEach(menuScript)

        assertReplays(live, play.sealed())
    }

    /** Without the adopt's setting move the replay would derive AUTO marks the player never saw. */
    @Test
    fun `S2 a play begun with Auto-cross off replays without system marks from its first frame`() {
        val play = fresh(MoveLog.fresh(QueensDefinition.id), autoCross = false)
        val live = play.liveAfterEach(settingsScript)

        assertTrue("the live queen at 0.6 s stands bare", live.getValue(600L).marks.values.none { it.kind == MarkKind.AUTO })
        assertReplays(live, play.sealed())
    }

    /** S3. The restore states the setting again; the log already ends on that value, so it adds nothing. */
    @Test
    fun `S3 save, kill, restore, win seals the bytes of the uninterrupted replay`() {
        val whole = fresh(MoveLog.fresh(QueensDefinition.id))
        whole.run(menuScript)

        val killed = fresh(MoveLog.fresh(QueensDefinition.id))
        killed.run(openingHalf + background(4_500L))
        fakeNow = fakeNow.plusSeconds(3_600L) // an hour dead: never play time
        val back = restored(MoveLog.restored(QueensDefinition.id, killed.log.snapshot()), killed.saved.last(), autoCross = true)
        val live = back.liveAfterEach(closingHalf, fromMs = 4_500L)

        assertArrayEquals(encoded(8_000L, recordOf(autoCross = true, menuScript)), whole.sealed().toByteArray())
        assertArrayEquals(whole.sealed().toByteArray(), back.sealed().toByteArray())
        assertEquals(8_000L, back.won.single().time)
        assertEquals(1, back.won.single().hints)
        assertReplays(live, back.sealed())
    }

    /** The saved session comes back under the default (on), so the restore has to apply the setting even when it records nothing. */
    @Test
    fun `S3 a play saved and restored with Auto-cross off seals the bytes of the uninterrupted replay`() {
        val opening = listOf(cross(500L, 0), queen(600L, 0))
        val finish = queens(1_100L, 12, 23, 29, 34, 46, 49) + listOf(cross(2_300L, 59), winningQueen(2_400L, 59))
        val whole = fresh(MoveLog.fresh(QueensDefinition.id), autoCross = false)
        whole.run(opening + finish)

        val killed = fresh(MoveLog.fresh(QueensDefinition.id), autoCross = false)
        killed.run(opening + background(1_000L))
        val back = restored(MoveLog.restored(QueensDefinition.id, killed.log.snapshot()), killed.saved.last(), autoCross = false)
        val live = back.liveAfterEach(finish, fromMs = 1_000L)

        assertTrue("the restored board stays bare", live.getValue(1_000L).marks.values.none { it.kind == MarkKind.AUTO })
        assertArrayEquals(whole.sealed().toByteArray(), back.sealed().toByteArray())
        assertReplays(live, back.sealed())
    }

    /** Settings can change while the play is closed: the restore then records the setting it applies, at the banked time. */
    @Test
    fun `a play restored under a setting changed while it was closed replays the marks the player saw`() {
        val killed = fresh(MoveLog.fresh(QueensDefinition.id), autoCross = false)
        killed.run(listOf(cross(500L, 0), queen(600L, 0), background(1_000L)))
        assertTrue(killed.session.marks.values.none { it.kind == MarkKind.AUTO })

        val back = restored(MoveLog.restored(QueensDefinition.id, killed.log.snapshot()), killed.saved.last(), autoCross = true)
        val finish = queens(1_100L, 12, 23, 29, 34, 46, 49) + listOf(cross(2_300L, 59), winningQueen(2_400L, 59))
        val live = back.liveAfterEach(finish, fromMs = 1_000L)

        assertTrue("the restored board derives under ON", live.getValue(1_000L).marks.values.any { it.kind == MarkKind.AUTO })
        assertEquals(
            listOf(0L to AutoCross(false), 500L to Cross(Cell(0)), 600L to Place(Cell(0)), 1_000L to AutoCross(true))
                .map { (atMs, move) -> StampedMove(atMs, move.toReplayMove()) },
            back.recorded().take(4),
        )
        assertReplays(live, back.sealed())
    }

    /** A recording play again: the play feature adopts the puzzle on a new controller over a new log, then asks for it. */
    @Test
    fun `play again starts its log with the setting at 0 and stamps the new solve from its own clock`() {
        val again = fresh(MoveLog.fresh(QueensDefinition.id), autoCross = false)
        fakeNow = fakeNow.plusSeconds(2L) // the first adopt's clock window: never the new solve's time
        again.controller.playAgain()
        val live = again.liveAfterEach(settingsScript)

        assertEquals(recordOf(autoCross = false, settingsScript), again.recorded())
        assertEquals(2_800L, again.won.single().time)
        assertReplays(live, again.sealed())
    }

    @Test
    fun `a won play restored stays as it was won, with no second win, no new move and the same wall-clock moment`() {
        val done = fresh(MoveLog.fresh(QueensDefinition.id))
        done.run(menuScript)
        fakeNow = fakeNow.plusSeconds(3_600L)

        val back = restored(MoveLog.restored(QueensDefinition.id, done.log.snapshot()), done.saved.last(), autoCross = true)
        back.board.onTap(5)

        assertTrue(back.controller.isComplete)
        assertEquals(Instant.parse("2026-11-02T00:01:08Z"), back.session.completedAt)
        assertArrayEquals(done.saved.last(), QueensSessionCodec.serialize(back.session))
        assertEquals(emptyList<CompletionRecord>(), back.won)
        assertEquals(done.recorded(), back.recorded())
    }

    @Test
    fun `S4 every stamp is the play time its input landed at, never decreasing, the last within the recorded time`() {
        val menu = fresh(MoveLog.fresh(QueensDefinition.id))
        menu.run(menuScript)
        val menuMoves = menu.recorded()
        assertEquals(
            listOf(
                0L, 1_000L, 1_500L, 1_900L, 2_400L, 3_000L, 3_010L, 3_020L, 3_500L, 3_510L, 4_000L, 4_200L,
                5_000L, 5_500L, 5_600L, 6_000L, 6_500L, 6_600L,
                6_700L, 6_800L, 6_900L, 7_000L, 7_100L, 7_200L, 7_300L, 7_400L, 7_500L, 7_600L, 7_700L, 7_800L, 7_900L, 8_000L,
            ),
            menuMoves.map { it.atMs },
        )
        assertEquals(recordOf(autoCross = true, menuScript), menuMoves)
        assertEquals(8_000L, menu.won.single().time)
        assertNeverBackAndWithin(menuMoves, menu.won.single().time)

        // The flip at 1 s arrives half a minute of wall time into the solve, while the app is away.
        val settings = fresh(MoveLog.fresh(QueensDefinition.id), autoCross = false)
        settings.run(settingsScript)
        val settingsMoves = settings.recorded()
        assertEquals(
            listOf(
                0L, 500L, 600L, 1_000L, 1_200L,
                1_300L, 1_400L, 1_500L, 1_600L, 1_700L, 1_800L, 1_900L, 2_000L, 2_100L, 2_200L, 2_300L, 2_400L, 2_500L, 2_600L,
                2_700L, 2_800L,
            ),
            settingsMoves.map { it.atMs },
        )
        assertEquals(recordOf(autoCross = false, settingsScript), settingsMoves)
        assertEquals(2_800L, settings.won.single().time)
        assertNeverBackAndWithin(settingsMoves, settings.won.single().time)
    }

    private fun assertNeverBackAndWithin(moves: List<StampedMove>, recordedMs: Long) {
        assertTrue(moves.zipWithNext().all { (earlier, later) -> earlier.atMs <= later.atMs })
        assertTrue(moves.last().atMs <= recordedMs)
    }

    @Test
    fun `S5 recording changes neither the completion record nor one saved session`() {
        for ((autoCross, script) in listOf(true to menuScript, false to settingsScript)) {
            val off = fresh(MoveLog.off(), autoCross)
            off.run(script)
            val on = fresh(MoveLog.fresh(QueensDefinition.id), autoCross)
            on.run(script)

            assertNull(off.log.snapshot())
            assertEquals(recordOf(autoCross, script).size, on.recorded().size)
            assertEquals(off.won, on.won)
            assertEquals(off.saved.size, on.saved.size)
            off.saved.zip(on.saved).forEachIndexed { i, (a, b) -> assertArrayEquals("save $i", a, b) }
        }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-11-02T00:00:00Z")
    }
}
