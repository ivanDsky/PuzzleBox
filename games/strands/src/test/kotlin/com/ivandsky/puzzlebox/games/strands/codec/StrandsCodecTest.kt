package com.ivandsky.puzzlebox.games.strands.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import com.ivandsky.puzzlebox.games.strands.tinyPuzzle
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsCodecTest {

    private val real = StrandsFixtures.puzzle(LocalDate.of(2026, 11, 5))

    @Test
    fun `real day round-trips`() {
        assertEquals(real, StrandsCodec.decode(StrandsCodec.encode(real)).getOrNull())
    }

    @Test
    fun `encoding is canonical whatever the valid-word order`() {
        val reordered = real.copy(validWords = real.validWords.reversed().toSet())
        val bytes = StrandsCodec.encode(real)
        assertArrayEquals(bytes, StrandsCodec.encode(reordered))
        assertArrayEquals(bytes, StrandsCodec.encode(StrandsCodec.decode(bytes).getOrNull()!!))
    }

    @Test
    fun `counts above 255 survive as varints`() {
        assertTrue(real.validWords.size > 255)
        assertEquals(real.validWords, StrandsCodec.decode(StrandsCodec.encode(real)).getOrNull()!!.validWords)
    }

    @Test
    fun `emoji clue round-trips`() {
        assertEquals("Feline 🐈", StrandsCodec.decode(StrandsCodec.encode(tinyPuzzle)).getOrNull()!!.clue)
    }

    @Test
    fun `unknown version is unsupported`() {
        val bytes = StrandsCodec.encode(tinyPuzzle).also { it[0] = 9 }
        assertEquals(
            DecodeResult.Err(DecodeError.UnsupportedVersion(found = 9, maxKnown = StrandsCodec.VERSION)),
            StrandsCodec.decode(bytes),
        )
    }

    @Test
    fun `every truncation is malformed`() {
        val bytes = StrandsCodec.encode(tinyPuzzle)
        for (len in 0 until bytes.size) {
            val result = StrandsCodec.decode(bytes.copyOf(len))
            assertTrue("prefix $len: $result", (result as DecodeResult.Err).error is DecodeError.Malformed)
        }
    }

    @Test
    fun `trailing bytes are malformed`() {
        val result = StrandsCodec.decode(StrandsCodec.encode(tinyPuzzle) + byteArrayOf(0))
        assertTrue((result as DecodeResult.Err).error is DecodeError.Malformed)
    }

    @Test
    fun `envelope is the canonical bytes under the strands variant`() {
        val envelope = StrandsCodec.envelope(tinyPuzzle)
        assertEquals(StrandsDefinition.id, envelope.gameId)
        assertEquals(StrandsDefinition.VARIANT, envelope.variant)
        assertEquals(StrandsCodec.VERSION, envelope.formatVersion)
        assertArrayEquals(StrandsCodec.encode(tinyPuzzle), envelope.payload)
        assertEquals(envelope, StrandsDefinition.decode(envelope.payload).getOrNull())
        assertArrayEquals(envelope.payload, StrandsDefinition.encode(envelope))
    }
}
