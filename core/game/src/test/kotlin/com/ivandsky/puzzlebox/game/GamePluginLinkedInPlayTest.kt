package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Companion.mark
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Session
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamePluginLinkedInPlayTest {

    private val moves = listOf(StampedMove(100, mark(3)), StampedMove(400, mark(1)), StampedMove(900, mark(4)))
    private val bytes = sealed(TestGame.id)
    private val encoded = LinkedInPlay(mapOf("k" to LinkedInPlayValue.Number(1)), mapOf("a" to "k"), mistakeFree = true)

    @Test
    fun `a plugin without an encoder gives null`() =
        assertNull(plugin(CountingReplayGame(goal = 3), null).linkedInPlayOf(TestGame.Definition.blank(), bytes))

    @Test
    fun `a plugin without a replay member gives null`() =
        assertNull(plugin(replay = null) { encoded }.linkedInPlayOf(TestGame.Definition.blank(), bytes))

    @Test
    fun `a puzzle the plugin rejects gives null`() {
        val foreign = Puzzle(GameId("other"), TestGame.variant, 1, ByteArray(0))
        assertNull(plugin(CountingReplayGame(goal = 3)) { encoded }.linkedInPlayOf(foreign, bytes))
    }

    @Test
    fun `bytes that do not fold give null and never reach the encoder`() {
        var called = false
        val plugin = plugin(CountingReplayGame(goal = 3)) { called = true; encoded }

        assertNull(plugin.linkedInPlayOf(TestGame.Definition.blank(), sealed(GameId("other"))))
        assertNull(plugin.linkedInPlayOf(TestGame.Definition.blank(), SolveReplay.of(byteArrayOf(1, 2, 3))!!))
        assertEquals(false, called)
    }

    @Test
    fun `the encoder gets the session after every tick in order and the tick kinds, and its play comes back`() {
        var seen: LinkedInSolve<Session>? = null
        val play = plugin(CountingReplayGame(goal = 3)) { seen = it; encoded }
            .linkedInPlayOf(TestGame.Definition.blank(), bytes)

        assertEquals(encoded, play)
        val solve = checkNotNull(seen)
        assertEquals(listOf(1, 2, 3), solve.boards.map { it.marks })
        assertEquals(listOf(3L, 94L, 2_918L), solve.boards.map { it.trail })
        assertEquals(List(3) { MoveKind.Normal }, solve.kinds)
        assertEquals(Session(marks = 3, trail = 2_918, markedAtMs = 900), solve.final)
    }

    @Test
    fun `an encoder that declines gives null`() =
        assertNull(plugin(CountingReplayGame(goal = 3)) { null }.linkedInPlayOf(TestGame.Definition.blank(), bytes))

    private fun sealed(game: GameId): SolveReplay {
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(game, ReplayOutcome.Solved, endMs = 1_000, moves)))
        return checkNotNull(SolveReplay.of(stored))
    }

    private fun plugin(
        replay: GameReplay<Session>?,
        encoder: ((LinkedInSolve<Session>) -> LinkedInPlay?)?,
    ): GamePlugin<TestGame.State, Session, TestGame.Model> = GamePlugin(
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
        linkedInPlay = encoder,
    )
}
