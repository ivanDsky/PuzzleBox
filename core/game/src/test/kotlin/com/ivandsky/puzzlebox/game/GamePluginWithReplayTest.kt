package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.game.replay.CountingReplayGame
import com.ivandsky.puzzlebox.game.replay.CountingReplayGame.Session
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GamePluginWithReplayTest {

    private val own = CountingReplayGame(goal = 3)
    private val other = CountingReplayGame(goal = 5)
    private val walker = TechniqueWalker { emptyMap() }

    private val plugin: GamePlugin<TestGame.State, Session, TestGame.Model> = GamePlugin(
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
        replay = own,
        techniqueWalker = walker,
    )

    @Test
    fun `withReplay swaps the replay and shares every other member`() {
        val swapped = plugin.withReplay(other)

        assertSame(other, swapped.replay)
        assertSame(own, plugin.replay)
        assertSame(plugin.definition, swapped.definition)
        assertSame(plugin.rules, swapped.rules)
        assertSame(plugin.session, swapped.session)
        assertSame(plugin.score, swapped.score)
        assertSame(plugin.stateOf, swapped.stateOf)
        assertSame(plugin.initialSession, swapped.initialSession)
        assertSame(plugin.refOf, swapped.refOf)
        assertSame(plugin.playAgainSession, swapped.playAgainSession)
        assertSame(plugin.rendererModel, swapped.rendererModel)
        assertSame(plugin.editor, swapped.editor)
        assertSame(plugin.linkedInPlay, swapped.linkedInPlay)
        assertSame(plugin.techniqueWalker, swapped.techniqueWalker)
    }

    @Test
    fun `withReplay keeps the technique walker`() {
        assertSame(walker, plugin.withReplay(other).techniqueWalker)
        assertSame(walker, plugin.withReplay(null).techniqueWalker)
    }

    @Test
    fun `withReplay null drops the replay`() = assertNull(plugin.withReplay(null).replay)
}
