package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsReplayMove
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.engine.StrandsHint
import com.ivandsky.puzzlebox.games.strands.engine.StrandsRules
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.games.strands.withHint
import com.ivandsky.puzzlebox.games.strands.withSubmit
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
 * The Strands controller records what it applies: a fixed script runs through the real
 * controller on a fake clock, and the sessions, the log, the replay and the completion are read back.
 */
class StrandsReplayRecordingTest {

    private var fakeNow: Instant = START

    /** One play: the real controller with its log and what it handed to the play feature. */
    private inner class Play(val log: MoveLog) {
        val saved = ArrayList<ByteArray>()
        val won = ArrayList<CompletionRecord>()
        val controller = StrandsPlayController(
            env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }, log),
            now = { fakeNow },
        )
        val session: StrandsSession get() = checkNotNull(controller.session)

        /** Runs [script] from play time [fromMs]; [afterEach] sees the play right after every input. */
        fun run(script: List<Input>, fromMs: Long = 0L, afterEach: (Input) -> Unit = {}) {
            var playMs = fromMs
            for (input in script) {
                fakeNow = fakeNow.plusMillis(input.atMs - playMs)
                playMs = input.atMs
                input.act(controller)
                afterEach(input)
            }
        }
    }

    /**
     * One scripted input, landing at play time [atMs]. [move] is what the log must hold for it
     * (null = nothing). [today] is the reducer call the controller made for it before it recorded anything.
     */
    private class Input(
        val atMs: Long,
        val move: StrandsReplayMove?,
        val today: (StrandsSession) -> StrandsSession,
        val act: StrandsPlayController.() -> Unit,
    )

    private fun submit(atMs: Long, path: List<Coord>, changes: Boolean = true) = Input(
        atMs = atMs,
        move = StrandsReplayMove.Submit(path).takeIf { changes },
        today = { it.withSubmit(path) },
        act = { onTrace(TraceStep.Submit(path)) },
    )

    private fun word(atMs: Long, text: String) = submit(atMs, StrandsUiFixtures.nonTheme.getValue(text))

    private fun row(atMs: Long, r: Int) = submit(atMs, StrandsUiFixtures.row(r))

    /** A press of the hint button; [move] is null when it only opens a dialog. */
    private fun hint(atMs: Long, move: StrandsReplayMove.Hint?) = Input(
        atMs = atMs,
        move = move,
        today = { s -> (StrandsRules.hint(s.state) as? StrandsHint.Reveal)?.let(s::withHint) ?: s },
        act = { pressHint() },
    )

    private fun inFlight(atMs: Long) = Input(atMs, null, { it }) {
        tap(Coord(0, 0))
        tap(Coord(0, 1))
        onTrace(TraceStep.Cleared)
    }

    private fun background(atMs: Long) = Input(atMs, null, { it }) { onBackground() }

    /** Valid words that are not theme words earn the first hint, a refused path and a repeat change nothing. */
    private val opening = listOf(
        word(1_000L, "PIKE"),
        inFlight(1_200L),
        word(1_500L, "TAKE"),
        word(2_000L, "RAKE"),
        hint(2_500L, StrandsReplayMove.Hint(0, HintStage.Outlined)),
        submit(2_600L, StrandsUiFixtures.nonTheme.getValue("PIKE"), changes = false),
        submit(3_000L, listOf(Coord(0, 0), Coord(3, 3)), changes = false),
        word(3_500L, "CART"),
        word(3_600L, "KART"),
        word(3_700L, "RAKES"),
    )

    private val closing = listOf(
        hint(4_000L, StrandsReplayMove.Hint(0, HintStage.OrderRevealed)),
        hint(4_100L, null),
        row(4_500L, 0),
        row(5_000L, 1),
        row(5_500L, 2),
        row(6_000L, 3),
    )

    private val script = opening + closing

    private fun fresh(log: MoveLog = MoveLog.off()): Play {
        fakeNow = START
        val play = Play(log)
        assertEquals(Adopt.Adopted, play.controller.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref))
        return play
    }

    /** A killed play comes back: a new controller over the saved [log] snapshot and the saved session [bytes]. */
    private fun restored(log: MoveLog, bytes: ByteArray): Play {
        val play = Play(log)
        assertEquals(Restored(StrandsUiFixtures.ref), play.controller.adoptRestored(bytes))
        return play
    }

    /** The live session by play time: at [fromMs], then after the last input that landed at each time. */
    private fun Play.liveAfterEach(script: List<Input>, fromMs: Long = 0L): Map<Long, StrandsSession> {
        val live = linkedMapOf(fromMs to session)
        run(script, fromMs) { live[it.atMs] = session }
        return live
    }

    /** What a replay reproduces of a live session: the rules state, not the banked clock. */
    private fun StrandsSession.clockFree(): StrandsSession = copy(elapsedMs = 0L)

    private fun Play.sealed(): SolveReplay =
        checkNotNull(log.seal(ReplayOutcome.Solved, won.single().time)) { "the play sealed no replay" }

    private fun Play.recorded(): List<StampedMove> =
        checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "the play has no readable log" }.moves

    private fun timelineOf(sealed: SolveReplay): Timeline =
        checkNotNull(strandsPlugin.openTimeline(StrandsUiFixtures.envelope(), sealed)) { "the sealed replay does not open" }

    /** S2 for one play: every live session is the replay's frame at its play time, and the replay ends solved. */
    private fun assertReplays(live: Map<Long, StrandsSession>, sealed: SolveReplay) {
        val timeline = timelineOf(sealed)
        for ((atMs, session) in live) {
            assertEquals("frame at $atMs ms", session.state, (timeline.at(atMs).session as StrandsSession).state)
        }
        val end = timeline.at(timeline.endMs)
        assertTrue(end.ended)
        assertTrue(end.facts.solved)
        assertTrue((end.session as StrandsSession).state.isComplete)
    }

    /** S5 before the rewrite and after it: each saved session is what the direct reducer call gives, input for input. */
    @Test
    fun `the controller stores what today's reducer calls give, input for input`() {
        val play = fresh()
        var expected = play.session
        var playMs = 0L
        for (input in script) {
            expected = input.today(expected)
            play.run(listOf(input), fromMs = playMs)
            playMs = input.atMs
            assertEquals("after ${input.atMs} ms", expected.clockFree(), play.session.clockFree())
        }
        assertTrue(play.session.state.isComplete)
        assertEquals(6_000L, play.won.single().time)
        assertEquals(2, play.won.single().hints)
        assertEquals(6_000L, play.session.elapsedMs)
    }

    /** A refused, repeated or unfinished input hands back the session it was given: nothing is stored or saved for it. */
    @Test
    fun `an input that changes nothing leaves the session instance and the saves alone`() {
        val play = fresh(MoveLog.fresh(StrandsDefinition.id))
        var playMs = 0L
        for (input in script) {
            val before = play.session
            val saves = play.saved.size
            play.run(listOf(input), fromMs = playMs)
            playMs = input.atMs
            if (input.move == null) {
                assertTrue("${input.atMs} ms", before === play.session)
                assertEquals("${input.atMs} ms", saves, play.saved.size)
            } else {
                assertEquals("${input.atMs} ms", saves + 1, play.saved.size)
            }
        }
    }

    @Test
    fun `S2 every session of a solve is the replay's frame at its stamp, and the end is the solved board`() {
        val play = fresh(MoveLog.fresh(StrandsDefinition.id))
        val live = play.liveAfterEach(script)

        assertReplays(live, play.sealed())
    }

    @Test
    fun `S3 save, kill, restore, win seals the bytes of the uninterrupted replay`() {
        val whole = fresh(MoveLog.fresh(StrandsDefinition.id))
        whole.run(script)

        val killed = fresh(MoveLog.fresh(StrandsDefinition.id))
        killed.run(opening + background(3_700L))
        fakeNow = fakeNow.plusSeconds(3_600L) // an hour dead: never play time
        val back = restored(MoveLog.restored(StrandsDefinition.id, killed.log.snapshot()), killed.saved.last())
        val live = back.liveAfterEach(closing, fromMs = 3_700L)

        assertArrayEquals(whole.sealed().toByteArray(), back.sealed().toByteArray())
        assertEquals(6_000L, back.won.single().time)
        assertEquals(2, back.won.single().hints)
        assertEquals(whole.session, back.session)
        assertReplays(live, back.sealed())
    }

    @Test
    fun `S4 every stamp is the play time its input landed at, never decreasing, the last within the recorded time`() {
        val play = fresh(MoveLog.fresh(StrandsDefinition.id))
        play.run(script)

        val moves = play.recorded()
        assertEquals(
            listOf(1_000L, 1_500L, 2_000L, 2_500L, 3_500L, 3_600L, 3_700L, 4_000L, 4_500L, 5_000L, 5_500L, 6_000L),
            moves.map { it.atMs },
        )
        assertEquals(
            script.mapNotNull { input -> input.move?.let { StampedMove(input.atMs, it.toReplayMove(StrandsUiFixtures.puzzle.cols)) } },
            moves,
        )
        assertEquals(moves.map { it.atMs }.sorted(), moves.map { it.atMs })
        assertTrue(moves.last().atMs <= play.won.single().time)
    }

    @Test
    fun `S5 recording changes neither the completion record nor one saved session`() {
        val off = fresh(MoveLog.off())
        off.run(script)
        val on = fresh(MoveLog.fresh(StrandsDefinition.id))
        on.run(script)

        assertNull(off.log.snapshot())
        assertEquals(script.count { it.move != null }, on.recorded().size)
        assertEquals(off.won, on.won)
        assertEquals(off.saved.size, on.saved.size)
        off.saved.zip(on.saved).forEachIndexed { i, (a, b) -> assertArrayEquals("save $i", a, b) }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-11-05T00:00:00Z")
    }
}
