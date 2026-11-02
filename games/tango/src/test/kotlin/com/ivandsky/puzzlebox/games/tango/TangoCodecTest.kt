package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoCodecTest {

    @Test
    fun `decode round-trips the bundled sample`() {
        val bytes = TangoTestSupport.sampleBytes
        val decoded = TangoCodec.decode(bytes)
        assertTrue(decoded is DecodeResult.Ok)
        assertEquals(TangoTestSupport.sample, (decoded as DecodeResult.Ok).value)
        assertTrue(TangoCodec.encode(decoded.value).contentEquals(bytes))
    }

    @Test
    fun `encode is canonical regardless of construction order`() {
        val reversed = TangoTestSupport.sample.copy(
            markers = TangoTestSupport.sample.markers.reversed(),
            givens = TangoTestSupport.sample.givens.toMap().let { LinkedHashMap<Int, TangoCell>().apply { it.entries.reversed().forEach { e -> put(e.key, e.value) } } },
        )
        assertTrue(TangoCodec.encode(reversed).contentEquals(TangoCodec.encode(TangoTestSupport.sample)))
    }

    @Test
    fun `a newer version is dropped as unsupported`() {
        val bytes = TangoTestSupport.sampleBytes.clone().also { it[0] = 2 }
        assertEquals(
            DecodeError.UnsupportedVersion(found = 2, maxKnown = 1),
            (TangoCodec.decode(bytes) as DecodeResult.Err).error,
        )
    }

    @Test
    fun `a size contradicting the variant is rejected`() {
        val bytes = TangoTestSupport.sampleBytes.clone().also { it[10] = 7 } // n byte
        assertTrue(TangoCodec.decode(bytes) is DecodeResult.Err)
    }

    @Test
    fun `truncated bytes are malformed, never thrown on`() {
        val bytes = TangoTestSupport.sampleBytes.copyOf(8)
        assertTrue(TangoCodec.decode(bytes) is DecodeResult.Err)
    }

    @Test
    fun `unknown tail bytes survive decode and re-encode verbatim`() {
        val extended = TangoTestSupport.sampleBytes + byteArrayOf(0x7F, 0x01)
        val decoded = (TangoCodec.decode(extended) as DecodeResult.Ok).value
        assertTrue(decoded.unknownTail.contentEquals(byteArrayOf(0x7F, 0x01)))
        assertTrue(TangoCodec.encode(decoded).contentEquals(extended))
    }

    @Test
    fun `markers cannot sit on boundary edges`() {
        val out = ByteArrayOutputStream()
        out.write(1) // version
        out.write(8)
        out.write("tango/n6".toByteArray(Charsets.UTF_8))
        out.write(6) // n
        out.write(0) // givenCount
        out.write(1) // markerCount: cell 5 RIGHT on a 6-wide board is out of range
        out.write(5)
        out.write(0)
        out.write(0)
        assertTrue(TangoCodec.decode(out.toByteArray()) is DecodeResult.Err)
    }
}
