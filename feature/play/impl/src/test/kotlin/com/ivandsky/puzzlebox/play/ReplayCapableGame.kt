package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.FixtureSession
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures

/** A registry whose one game, [ReplayFixtures.GAME], opens a `ReplayFixtures.sealed(SHORT_MOVES)` replay. */
internal fun replayCapableGames(): GameRegistry {
    val base = TestGames.plugin(ReplayFixtures.GAME)
    val plugin = GamePlugin(
        definition = base.definition,
        rules = base.rules,
        session = object : GameSessionCodec<FixtureSession> {
            override fun serialize(session: FixtureSession): ByteArray = ByteArray(0)
            override fun deserialize(bytes: ByteArray): DecodeResult<FixtureSession> =
                DecodeResult.Ok(ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES))
        },
        score = object : ScoreModel<FixtureSession> {
            override fun completion(session: FixtureSession): CompletionRecord? = null
        },
        stateOf = { },
        initialSession = { _: Puzzle -> DecodeResult.Ok(ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES)) },
        refOf = base.refOf,
        playAgainSession = base.playAgainSession,
        rendererModel = { _, _, _ -> TestGames.Model },
        replay = ReplayFixtures.replay,
    )
    return GameRegistry(listOf(plugin))
}
