package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.game.replay.CountingReplayGame
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Companion.mark
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Session
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamePluginOpenTimelineTest {

    private val moves = listOf(StampedMove(100, mark(3)), StampedMove(400, mark(1)), StampedMove(900, mark(4)))
    private val bytes = sealed(TestGame.id)

    @Test
    fun `a plugin without a replay member gives null`() =
        assertNull(plugin(replay = null).openTimeline(TestGame.Definition.blank(), bytes))

    @Test
    fun `a puzzle the plugin rejects gives null`() {
        val foreign = Puzzle(GameId("other"), TestGame.variant, 1, ByteArray(0))
        assertNull(plugin(CountingReplayGame(goal = 3)).openTimeline(foreign, bytes))
    }

    @Test
    fun `bytes of another game give null`() =
        assertNull(plugin(CountingReplayGame(goal = 3)).openTimeline(TestGame.Definition.blank(), sealed(GameId("other"))))

    @Test
    fun `a plugin with a replay member gives the frames Timeline open gives`() {
        val direct = checkNotNull(Timeline.open(TestGame.id, CountingReplayGame(goal = 3), Session(), bytes))
        val viaPlugin = checkNotNull(plugin(CountingReplayGame(goal = 3)).openTimeline(TestGame.Definition.blank(), bytes))

        assertEquals(TestGame.id, viaPlugin.game)
        assertEquals(direct.endMs, viaPlugin.endMs)
        assertEquals(direct.outcome, viaPlugin.outcome)
        assertArrayEquals(longArrayOf(100, 400, 900), viaPlugin.moveTimesMs)
        for (tMs in longArrayOf(-1, 0, 100, 250, 400, 899, 900, 1_000, 2_000)) {
            assertEquals("frame at $tMs", fieldsOf(direct.at(tMs)), fieldsOf(viaPlugin.at(tMs)))
        }
        assertEquals(Session(marks = 3, trail = 2_918, markedAtMs = 900), viaPlugin.at(1_000).session)
    }

    private fun sealed(game: GameId): SolveReplay {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(game, ReplayOutcome.Solved, endMs = 1_000, moves)))
        return checkNotNull(SolveReplay.of(stored))
    }

    private fun fieldsOf(frame: ReplayFrame): List<Any?> =
        listOf(frame.session, frame.before, frame.facts, frame.atMs, frame.sinceMoveMs, frame.ended)

    /** [TestGame]'s plugin over the fake's session type; only `initialSession` and `replay` matter here. */
    private fun plugin(replay: GameReplay<Session>?): GamePlugin<TestGame.State, Session, TestGame.Model> = GamePlugin(
        TestGame.Definition,
        TestGame.Rules,
        session = object : GameSessionCodec<Session> {
            override fun serialize(session: Session): ByteArray = ByteArray(0)
            override fun deserialize(bytes: ByteArray): DecodeResult<Session> = DecodeResult.Ok(Session())
        },
        score = object : ScoreModel<Session> {
            override fun completion(session: Session): CompletionRecord? = null
        },
        stateOf = { TestGame.State() },
        initialSession = { puzzle -> TestGame.initialSession(puzzle).map { Session() } },
        refOf = { key -> TestGame.KeyRef(key) },
        playAgainSession = { cached, _ -> DecodeResult.Ok(cached.payload) },
        rendererModel = { state, _, lastVerdict -> TestGame.Model(state.solved, lastVerdict) },
        replay = replay,
    )
}
