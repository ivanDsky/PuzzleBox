package com.ivandsky.puzzlebox.games.tangoui

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
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Clear
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Hint
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Place
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Redo
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Rejection
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Undo
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
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
 * The Tango controller records what it applies: one fixed script runs through the real controller on a
 * fake clock, and the log, the replay and the completion are read back.
 */
class TangoReplayRecordingTest {

    private var fakeNow: Instant = START

    /** One play: the real controller, its log and what it handed to the play feature. */
    private inner class Play(val log: MoveLog) {
        val saved = ArrayList<ByteArray>()
        val won = ArrayList<CompletionRecord>()
        val controller = TangoPlayController(
            env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }, log),
            now = { fakeNow },
            hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
        )
        val session: TangoSession get() = checkNotNull(controller.session)

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
     * One scripted input. It lands at play time [atMs], after [awayMs] more of wall time that is not play.
     * [moves] is what the log must hold for it (none = nothing). [today] is the reducer calls the controller made
     * for it before it recorded anything, with the wall-clock instant it passed.
     */
    private class Input(
        val atMs: Long,
        val moves: List<TangoReplayMove>,
        val awayMs: Long = 0L,
        val today: TangoSession.(Instant) -> TangoSession = { this },
        val act: Play.() -> Unit,
    )

    private fun fresh(log: MoveLog = MoveLog.off()): Play {
        fakeNow = START
        val play = Play(log)
        assertEquals(Adopt.Adopted, play.controller.adoptToday(TangoPlayFixture.envelope(), ref))
        return play
    }

    /** A killed play comes back: a new controller over the saved [log] snapshot and the saved session [bytes]. */
    private fun restored(log: MoveLog, bytes: ByteArray): Play {
        val play = Play(log)
        assertEquals(Restored(ref), play.controller.adoptRestored(bytes))
        return play
    }

    private fun Play.liveAfterEach(script: List<Input>, fromMs: Long = 0L): Map<Long, TangoSession> {
        val live = linkedMapOf(fromMs to session)
        run(script, fromMs) { live[it.atMs] = session }
        return live
    }

    /**
     * What a replay reproduces of a live session: board, undo history and counters. Not the record identity,
     * the banked clock or the wall-clock win.
     */
    private fun TangoSession.clockFree(): TangoSession =
        copy(ref = null, elapsedMs = 0L, completedAt = completedAt?.let { Instant.EPOCH })

    private fun Play.sealed(): SolveReplay =
        checkNotNull(log.seal(ReplayOutcome.Solved, won.single().time)) { "the play sealed no replay" }

    private fun Play.recorded(): List<StampedMove> =
        checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "the play has no readable log" }.moves

    private fun timelineOf(sealed: SolveReplay): Timeline =
        checkNotNull(tangoPlugin.openTimeline(TangoPlayFixture.envelope(), sealed)) {
            "the sealed replay does not open"
        }

    private fun Timeline.sessionAt(atMs: Long): TangoSession = at(atMs).session as TangoSession

    /** S2 for one play: every live session is the replay's frame at its play time, and the replay ends solved. */
    private fun assertReplays(live: Map<Long, TangoSession>, sealed: SolveReplay) {
        val timeline = timelineOf(sealed)
        for ((atMs, session) in live) {
            assertEquals("frame at $atMs ms", session.clockFree(), timeline.sessionAt(atMs).clockFree())
        }
        val end = timeline.at(timeline.endMs)
        assertTrue(end.ended)
        assertTrue(end.facts.solved)
        assertEquals(TangoPlayFixture.solution, (end.session as TangoSession).grid())
    }

    private fun recordOf(script: List<Input>): List<StampedMove> =
        script.flatMap { input -> input.moves.map { StampedMove(input.atMs, it.toReplayMove()) } }

    private fun encoded(endMs: Long, moves: List<StampedMove>): ByteArray? =
        ReplayCodec.encode(ReplayRecord(TangoDefinition.id, ReplayOutcome.Solved, endMs, moves))

    //region scripts

    private fun place(atMs: Long, idx: Int, value: TangoCell, rejected: Boolean = false) = Input(
        atMs,
        listOf(Place(idx, value)) + if (rejected) listOf(Rejection) else emptyList(),
        today = {
            val moved = withMove(TangoMove(idx, value), it)
            if (rejected) moved.withRejection() else moved
        },
    ) { controller.onPlacement(TangoPlacement(idx, value)) }

    /** The last cell: the win folds the running window into the banked time, [atMs] of it. */
    private fun winning(atMs: Long, idx: Int, value: TangoCell) = Input(
        atMs,
        listOf(Place(idx, value)),
        today = { withMove(TangoMove(idx, value), it).copy(elapsedMs = atMs) },
    ) { controller.onPlacement(TangoPlacement(idx, value)) }

    private fun ignored(atMs: Long, act: Play.() -> Unit) = Input(atMs, moves = emptyList(), act = act)

    private fun background(atMs: Long) =
        Input(atMs, moves = emptyList(), today = { copy(elapsedMs = atMs) }) { controller.onBackground() }

    private fun foreground(atMs: Long, awayMs: Long) =
        Input(atMs, moves = emptyList(), awayMs = awayMs) { controller.onForeground() }

    /**
     * Every kind of input, a counted hint and a free one, then the inputs nothing records.
     * Ends with the clock banked at 3.6 s.
     */
    private val openingHalf: List<Input> = listOf(
        place(1_000L, 2, TangoCell.SUN, rejected = true), // three suns in a row: lands, counts a mistake
        Input(1_500L, listOf(Undo), today = { withUndo() }) { controller.undo() },
        Input(1_800L, listOf(Redo), today = { withRedo() }) { controller.redo() },
        place(2_000L, 2, TangoCell.MOON),
        ignored(2_200L) { controller.onPlacement(TangoPlacement(2, TangoCell.MOON)) }, // the value it has
        ignored(2_400L) { controller.onPlacement(TangoPlacement(0, TangoCell.MOON)) }, // a given
        Input(2_600L, listOf(Hint), today = { copy(hintsUsed = 1) }) { controller.requestHint() },
        ignored(2_700L) { controller.requestHint() }, // the same step again is free
        place(3_000L, 3, TangoCell.SUN),
        Input(3_200L, listOf(Clear), today = { withCleared() }) { controller.clearBoard() },
        Input(3_400L, listOf(Undo), today = { withUndo() }) { controller.undo() },
        ignored(3_500L) { controller.onPlacement(TangoPlacement(3, TangoCell.SUN)) }, // the value it has
    )

    /** A minute away from the app at play time 3.6 s. */
    private val pause: List<Input> = listOf(background(3_600L), foreground(3_600L, awayMs = 60_000L))

    /** The rest of the solution, one cell per 100 ms from 4 s; the last cell wins at 6.3 s. */
    private val closingHalf: List<Input> = TangoPlayFixture.placements
        .filter { (idx, _) -> idx != 2 && idx != 3 }
        .let { rest ->
            rest.mapIndexed { i, (idx, value) ->
                val atMs = 4_000L + 100L * i
                if (i == rest.lastIndex) winning(atMs, idx, value) else place(atMs, idx, value)
            }
        }

    private val script: List<Input> = openingHalf + pause + closingHalf

    //endregion

    /**
     * Characterization: written against the controller that called the session reducers itself,
     * and green there. Routing every change through the move log must not shift one session.
     */
    @Test
    fun `the controller stores what today's reducer calls give, input for input`() {
        for (log in listOf(MoveLog.off(), MoveLog.fresh(TangoDefinition.id))) {
            val play = fresh(log)
            var expected = TangoSession(ref, TangoPlayFixture.puzzle)
            assertEquals(expected, play.session)

            play.run(script) { input ->
                expected = input.today(expected, fakeNow)
                assertEquals("after the input at ${input.atMs} ms", expected, play.session)
            }

            assertEquals(TangoPlayFixture.solution, play.session.grid())
            assertEquals(START.plusMillis(60_000L + WIN_MS), play.session.completedAt)
            assertEquals(WIN_MS, play.won.single().time)
            assertEquals(1, play.won.single().hints)
        }
    }

    @Test
    fun `S2 the replay shows the live session after every input and ends on the solved board`() {
        val play = fresh(MoveLog.fresh(TangoDefinition.id))
        val live = play.liveAfterEach(script)

        assertReplays(live, play.sealed())
    }

    /** S3. The saved session and the saved log agree, so a restored play seals the bytes of the uninterrupted one. */
    @Test
    fun `S3 save, kill, restore, win seals the bytes of the uninterrupted replay`() {
        val whole = fresh(MoveLog.fresh(TangoDefinition.id))
        whole.run(script)

        val killed = fresh(MoveLog.fresh(TangoDefinition.id))
        killed.run(openingHalf + background(3_600L))
        fakeNow = fakeNow.plusSeconds(3_600L) // an hour dead: never play time
        val back = restored(MoveLog.restored(TangoDefinition.id, killed.log.snapshot()), killed.saved.last())
        val live = back.liveAfterEach(closingHalf, fromMs = 3_600L)

        assertArrayEquals(encoded(WIN_MS, recordOf(script)), whole.sealed().toByteArray())
        assertArrayEquals(whole.sealed().toByteArray(), back.sealed().toByteArray())
        assertEquals(WIN_MS, back.won.single().time)
        assertEquals(1, back.won.single().hints)
        assertReplays(live, back.sealed())
    }

    @Test
    fun `S4 every stamp is the play time its input landed at, never decreasing, the last within the recorded time`() {
        val play = fresh(MoveLog.fresh(TangoDefinition.id))
        play.run(script)
        val moves = play.recorded()

        assertEquals(
            listOf(
                1_000L, 1_000L, 1_500L, 1_800L, 2_000L, 2_600L, 3_000L, 3_200L, 3_400L,
            ) + closingHalf.map { it.atMs },
            moves.map { it.atMs },
        )
        assertEquals(recordOf(script), moves)
        assertEquals(WIN_MS, play.won.single().time)
        assertTrue(moves.zipWithNext().all { (earlier, later) -> earlier.atMs <= later.atMs })
        assertTrue(moves.last().atMs <= play.won.single().time)
    }

    @Test
    fun `S5 recording changes neither the completion record nor one saved session`() {
        val off = fresh(MoveLog.off())
        off.run(script)
        val on = fresh(MoveLog.fresh(TangoDefinition.id))
        on.run(script)

        assertNull(off.log.snapshot())
        assertEquals(recordOf(script).size, on.recorded().size)
        assertEquals(off.won, on.won)
        assertEquals(off.saved.size, on.saved.size)
        off.saved.zip(on.saved).forEachIndexed { i, (a, b) -> assertArrayEquals("save $i", a, b) }
        assertArrayEquals(off.saved.last(), on.saved.last())
    }

    private companion object {
        val START: Instant = Instant.parse("2026-11-02T00:00:00Z")
        val ref = TangoPlayFixture.ref(java.time.LocalDate.parse("2026-11-02"))

        /** Play time of the winning cell: 24 cells from 4 s, 100 ms apart. */
        const val WIN_MS = 6_300L
    }
}
