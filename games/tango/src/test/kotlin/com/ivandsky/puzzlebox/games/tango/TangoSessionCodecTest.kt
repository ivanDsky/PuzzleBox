package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoSessionCodecTest {

    private val session = TangoSession(
        ref = TangoPuzzleRef(LocalDate.of(2026, 11, 2), 42),
        puzzle = TangoTestSupport.sample,
        placements = mapOf(2 to TangoCell.MOON, 15 to TangoCell.SUN),
        hintsUsed = 1,
        mistakeCount = 3,
        elapsedMs = 65_000,
    )

    @Test
    fun `serialize round-trips every field`() {
        val restored = (TangoSessionCodec.deserialize(TangoSessionCodec.serialize(session)) as DecodeResult.Ok).value
        assertEquals(session.copy(unknownTail = ByteArray(0)), restored)
    }

    @Test
    fun `completedAt is carried`() {
        val completed = session.copy(completedAt = Instant.ofEpochMilli(1_746_192_000_000))
        val restored = (TangoSessionCodec.deserialize(TangoSessionCodec.serialize(completed)) as DecodeResult.Ok).value
        assertEquals(completed.completedAt, restored.completedAt)
    }

    @Test
    fun `a null ref and empty placements round-trip`() {
        val bare = TangoSession(ref = null, puzzle = TangoTestSupport.sample)
        val restored = (TangoSessionCodec.deserialize(TangoSessionCodec.serialize(bare)) as DecodeResult.Ok).value
        assertNull(restored.ref)
        assertEquals(emptyMap<Int, TangoCell>(), restored.placements)
    }

    @Test
    fun `a newer session version is dropped as unsupported`() {
        val bytes = TangoSessionCodec.serialize(session).clone().also { it[0] = 9 }
        assertEquals(
            DecodeError.UnsupportedVersion(found = 9, maxKnown = 1),
            (TangoSessionCodec.deserialize(bytes) as DecodeResult.Err).error,
        )
    }

    @Test
    fun `unknown tail survives verbatim`() {
        val withTail = session.copy(unknownTail = byteArrayOf(1, 2, 3))
        val bytes = TangoSessionCodec.serialize(withTail)
        val restored = (TangoSessionCodec.deserialize(bytes) as DecodeResult.Ok).value
        assertTrue(restored.unknownTail.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `truncated bytes are malformed`() {
        assertTrue(TangoSessionCodec.deserialize(byteArrayOf(1, 0x00)) is DecodeResult.Err)
    }
}
