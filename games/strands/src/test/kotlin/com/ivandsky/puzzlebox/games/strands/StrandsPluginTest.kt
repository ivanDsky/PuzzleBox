package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsPluginTest {

    private val envelope = StrandsCodec.envelope(tinyPuzzle)
    private val day = LocalDate.of(2026, 11, 5)

    @Test
    fun `a fetched envelope starts a fresh session`() {
        val session = strandsPlugin.initialSession(envelope).getOrNull()!!
        assertEquals(StrandsSession(ref = null, state = StrandsState(tinyPuzzle)), session)
        assertEquals(session.state, strandsPlugin.stateOf(session))
    }

    @Test
    fun `a foreign envelope is rejected`() {
        val foreign = Puzzle(GameId("zip"), VariantId("zip/n6"), 1, envelope.payload)
        val result = strandsPlugin.initialSession(foreign)
        assertTrue((result as DecodeResult.Err).error is DecodeError.Malformed)
    }

    @Test
    fun `play-again hands off a fresh session carrying the day`() {
        val key = PuzzleKey.Daily(day, 977)
        val blob = SessionBlob.decode(strandsPlugin.playAgainSession(envelope, key).getOrNull()!!)!!
        assertEquals(StrandsDefinition.id, blob.game)
        assertEquals(
            StrandsSession(ref = StrandsPuzzleRef(PuzzleKey.Daily(day, 977)), state = StrandsState(tinyPuzzle)),
            StrandsSessionCodec.deserialize(blob.session).getOrNull(),
        )
    }

    @Test
    fun `a custom key plays again carrying the friend's puzzle identity`() {
        val custom = PuzzleKey.Custom("K7Q2MX")
        val blob = SessionBlob.decode(strandsPlugin.playAgainSession(envelope, custom).getOrNull()!!)!!
        assertEquals(StrandsPuzzleRef(custom), StrandsSessionCodec.deserialize(blob.session).getOrNull()?.ref)
        assertEquals(StrandsPuzzleRef(custom), strandsPlugin.refOf(custom))
    }
}
