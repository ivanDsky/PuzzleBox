package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Extend
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Hint
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Redo
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Reset
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.RetractTo
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Undo
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Zip controller records what it applies: fixed scripts run through the real controller on
 * a fake clock, and the log, the replay and the completion are read back.
 */
class ZipReplayRecordingTest {

    private var fakeNow: Instant = START

    /** One play: the real controller with its log and what it handed to the play feature. */
    private inner class Play(val log: MoveLog, val envelope: Puzzle) {
        val saved = ArrayList<ByteArray>()
        val won = ArrayList<CompletionRecord>()
        val controller = ZipPlayController(
            env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }, log),
            now = { fakeNow },
        )
        val session: ZipSession get() = controller.session!!

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
     * play. [moves] is what the log must hold for it. [today] is what the controller did to the
     * session before it recorded anything, with the wall-clock instant it passed.
     */
    private class Input(
        val atMs: Long,
        val moves: List<ZipReplayMove>,
        val awayMs: Long = 0L,
        val today: ZipSession.(Instant) -> ZipSession = { this },
        val act: Play.() -> Unit,
    )

    private fun fresh(log: MoveLog = MoveLog.off(), envelope: Puzzle = ZipPlayFixture.envelope()): Play {
        fakeNow = START
        val play = Play(log, envelope)
        assertEquals(Adopt.Adopted, play.controller.adoptToday(envelope, ZipPlayFixture.ref(DAY)))
        return play
    }

    /** A killed play comes back: a new controller over the saved [log] snapshot and the saved session [bytes]. */
    private fun restored(log: MoveLog, bytes: ByteArray): Play {
        val play = Play(log, ZipPlayFixture.envelope())
        assertEquals(Restored(ZipPlayFixture.ref(DAY)), play.controller.adoptRestored(bytes))
        return play
    }

    private fun Play.liveAfterEach(script: List<Input>, fromMs: Long = 0L): Map<Long, ZipSession> {
        val live = linkedMapOf(fromMs to session)
        run(script, fromMs) { live[it.atMs] = session }
        return live
    }

    /**
     * What a replay reproduces of a live session: the trace, its undo history and the hint count.
     * Not the record identity, the clock, the wall-clock moment of the win or the mistake counter.
     */
    private fun ZipSession.clockFree(): ZipSession =
        copy(ref = null, elapsedMs = 0L, runningSince = null, mistakeCount = 0, completedAt = completedAt?.let { Instant.EPOCH })

    private fun Play.sealed(): SolveReplay =
        checkNotNull(log.seal(ReplayOutcome.Solved, won.single().time)) { "the play sealed no replay" }

    private fun Play.recorded(): List<StampedMove> =
        checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "the play has no readable log" }.moves

    private fun timelineOf(play: Play, sealed: SolveReplay): Timeline =
        checkNotNull(zipPlugin.openTimeline(play.envelope, sealed)) { "the sealed replay does not open" }

    /** S2 for one play: every live session is the replay's frame at its play time, and the replay ends solved. */
    private fun assertReplays(play: Play, live: Map<Long, ZipSession>) {
        val timeline = timelineOf(play, play.sealed())
        for ((atMs, session) in live) {
            assertEquals("frame at $atMs ms", session.clockFree(), (timeline.at(atMs).session as ZipSession).clockFree())
        }
        val end = timeline.at(timeline.endMs)
        assertTrue(end.ended)
        assertTrue(end.facts.solved)
        assertEquals(play.session.path, (end.session as ZipSession).path)
    }

    private fun recordOf(script: List<Input>): List<StampedMove> =
        script.flatMap { input -> input.moves.map { StampedMove(input.atMs, it.toReplayMove()) } }

    //region scripts

    private fun at(idx: Int) = GridCell(idx / 5, idx % 5)

    private fun extend(atMs: Long, idx: Int) = Input(
        atMs, listOf(Extend(Cell(idx))),
        today = { withMove(ZipMove.Extend(Cell(idx)), it) },
    ) { controller.onDragEvent(DragEvent.Extend(at(idx))) }

    private fun ignored(atMs: Long, act: Play.() -> Unit, today: ZipSession.(Instant) -> ZipSession = { this }) =
        Input(atMs, emptyList(), today = today, act = act)

    private fun background(atMs: Long) =
        Input(atMs, emptyList(), today = { onBackground(it) }) { controller.onBackground() }

    private fun foreground(atMs: Long, awayMs: Long) =
        Input(atMs, emptyList(), awayMs = awayMs, today = { onForeground(it) }) { controller.onForeground() }

    /** Every kind of trace action, a refused drop, a counted hint and the no-ops, ending with the first stretch of the solve. */
    private val openingHalf: List<Input> = listOf(
        extend(1_000L, 0),
        extend(1_100L, 1),
        extend(1_200L, 2),
        ignored(1_300L, { controller.onDragEvent(DragEvent.Extend(at(7))) }, { withRejection() }), // a wall: counts a mistake only
        ignored(1_350L, { controller.onDragEvent(DragEvent.Refused(at(7))) }),
        ignored(1_360L, { controller.onDragEvent(DragEvent.Retract(2)) }), // the head itself
        Input(1_400L, listOf(RetractTo(Cell(1))), today = { withMove(ZipMove.RetractTo(Cell(1)), it) }) {
            controller.onDragEvent(DragEvent.Retract(1))
        },
        Input(1_500L, listOf(Undo), today = { withUndo() }) { controller.undo() },
        Input(1_600L, listOf(Redo), today = { withRedo() }) { controller.redo() },
        ignored(1_700L, { controller.redo() }), // nothing left to redo
        Input(1_800L, listOf(Reset), today = { withMove(ZipMove.Reset, it) }) { controller.onDragEvent(DragEvent.Clear) },
        ignored(1_850L, { controller.onDragEvent(DragEvent.Clear) }), // the trace is already empty
        Input(1_900L, listOf(Undo), today = { withUndo() }) { controller.undo() },
        extend(1_950L, 2),
    )

    /** A minute away from the app at play time 2 s. */
    private val pause: List<Input> = listOf(background(2_000L), foreground(2_000L, awayMs = 60_000L))

    /** The counted hint places cell 3, then the trace runs to the end. Ends at 4 s. */
    private val closingHalf: List<Input> = listOf(
        Input(
            2_500L, listOf(Hint, Extend(Cell(3))),
            today = { withMove(ZipMove.Extend(Cell(3)), it).withHintApplied() },
        ) { controller.requestHint() },
    ) + listOf(4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14, 19, 18, 17, 16, 15, 20, 21, 22, 23, 24)
        .mapIndexed { i, idx -> extend(2_600L + 100L * i, idx) }

    private val solveScript: List<Input> = openingHalf + pause + closingHalf

    /** Win by the counted hint itself: the last placed cell finishes the trace and the hint still counts. */
    private val hintWinScript: List<Input> =
        listOf(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14, 19, 18, 17, 16, 15, 20, 21, 22, 23)
            .mapIndexed { i, idx -> extend(100L * (i + 1), idx) } +
            Input(
                2_500L, listOf(Hint, Extend(Cell(24))),
                today = { withMove(ZipMove.Extend(Cell(24)), it).withHintApplied() },
            ) { controller.requestHint() }

    //endregion

    /**
     * Characterization: written against the controller that called the session reducers itself,
     * and green there. Routing every change through the move log must not shift one session.
     */
    @Test
    fun `the controller stores what today's reducer calls give, input for input`() {
        for ((script, hints) in listOf(solveScript to 1, hintWinScript to 1)) {
            for (log in listOf(MoveLog.off(), MoveLog.fresh(ZipDefinition.id))) {
                val play = fresh(log)
                var expected = play.session

                play.run(script) { input ->
                    expected = input.today(expected, fakeNow)
                    assertEquals("after the input at ${input.atMs} ms", expected, play.session)
                }

                assertEquals(ZipPlayFixture.solution, play.session.path)
                assertEquals(hints, play.won.single().hints)
                assertEquals(script.last().atMs, play.won.single().time)
                assertEquals(fakeNow, play.session.completedAt)
            }
        }
    }

    /** A dead trace: the hint trims it free, then counts one hint and places the next cell. */
    @Test
    fun `a hint that trims a dead trace stores what today's reducer calls give`() {
        val dead = ZipPlayFixture.envelope(ZipPlayFixture.branchyPuzzle())
        val script = ZipPlayFixture.deadTrace().mapIndexed { i, cell -> extend(100L * (i + 1), cell.idx) } + Input(
            1_000L, listOf(RetractTo(Cell(3)), Hint, Extend(Cell(4))),
            today = { withMove(ZipMove.RetractTo(Cell(3)), it).withMove(ZipMove.Extend(Cell(4)), it).withHintApplied() },
        ) { controller.requestHint() }
        for (log in listOf(MoveLog.off(), MoveLog.fresh(ZipDefinition.id))) {
            val play = fresh(log, dead)
            var expected = play.session
            play.run(script) { input ->
                expected = input.today(expected, fakeNow)
                assertEquals("after the input at ${input.atMs} ms", expected, play.session)
            }
            assertEquals(ZipPlayFixture.solution.take(5), play.session.path)
            assertEquals(1, play.session.hintsUsed)
        }
        val recorded = fresh(MoveLog.fresh(ZipDefinition.id), dead)
        recorded.run(script)
        assertEquals(recordOf(script), recorded.recorded())
    }

    @Test
    fun `S2 the replay shows the live trace after every input and ends on the solved board`() {
        val play = fresh(MoveLog.fresh(ZipDefinition.id))
        val live = play.liveAfterEach(solveScript)

        assertEquals(recordOf(solveScript), play.recorded())
        assertReplays(play, live)
    }

    @Test
    fun `S2 a win by the hint replays solved with the hint counted`() {
        val play = fresh(MoveLog.fresh(ZipDefinition.id))
        val live = play.liveAfterEach(hintWinScript)

        assertReplays(play, live)
        assertEquals(1, (timelineOf(play, play.sealed()).at(Long.MAX_VALUE).session as ZipSession).hintsUsed)
    }

    /** S3. The saved log and the saved session come back together; the seal equals the uninterrupted run's. */
    @Test
    fun `S3 save, kill, restore, win seals the bytes of the uninterrupted replay`() {
        val whole = fresh(MoveLog.fresh(ZipDefinition.id))
        whole.run(solveScript)

        val killed = fresh(MoveLog.fresh(ZipDefinition.id))
        killed.run(openingHalf + background(2_000L))
        fakeNow = fakeNow.plusSeconds(3_600L) // an hour dead: never play time
        val back = restored(MoveLog.restored(ZipDefinition.id, killed.log.snapshot()), killed.saved.last())
        val live = back.liveAfterEach(closingHalf, fromMs = 2_000L)

        assertArrayEquals(whole.sealed().toByteArray(), back.sealed().toByteArray())
        assertEquals(whole.won.single().time, back.won.single().time)
        assertEquals(1, back.won.single().hints)
        assertReplays(back, live)
    }

    @Test
    fun `S4 stamps never decrease and the last one is within the completion time`() {
        val play = fresh(MoveLog.fresh(ZipDefinition.id))
        play.run(solveScript)

        val stamps = play.recorded().map { it.atMs }
        assertEquals(stamps.sorted(), stamps)
        assertTrue(stamps.last() <= play.won.single().time)
    }

    @Test
    fun `S5 recording changes neither the completion record nor the final session`() {
        for (script in listOf(solveScript, hintWinScript)) {
            val off = fresh(MoveLog.off()).also { it.run(script) }
            val on = fresh(MoveLog.fresh(ZipDefinition.id)).also { it.run(script) }

            assertEquals(off.won.single(), on.won.single())
            assertArrayEquals(off.saved.last(), on.saved.last())
            assertEquals(off.session, on.session)
            assertEquals(null, off.log.snapshot())
            assertTrue(on.recorded().isNotEmpty())
        }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-11-02T00:00:00Z")
        val DAY: LocalDate = LocalDate.of(2026, 11, 2)
    }
}
