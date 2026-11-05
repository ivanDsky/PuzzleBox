package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.model.GameId
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
    fun `replay hands off a fresh session carrying the day`() {
        val blob = SessionBlob.decode(strandsPlugin.replaySession(envelope, day, 977).getOrNull()!!)!!
        assertEquals(StrandsDefinition.id, blob.game)
        assertEquals(
            StrandsSession(ref = StrandsPuzzleRef(day, 977), state = StrandsState(tinyPuzzle)),
            StrandsSessionCodec.deserialize(blob.session).getOrNull(),
        )
    }

    @Test
    fun `refs and the renderer model come from the plugin`() {
        assertEquals(StrandsPuzzleRef(day, 977), strandsPlugin.refOf(day, 977))
        val state = StrandsState(tinyPuzzle)
        assertEquals(StrandsRendererModel.of(state), strandsPlugin.rendererModel(state, null, null))
    }
}
