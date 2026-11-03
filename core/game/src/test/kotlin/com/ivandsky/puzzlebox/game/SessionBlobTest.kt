package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The self-describing replay carrier (ADR-0005): encode/decode round-trips, corrupt payloads decode to null — never guessed through. */
class SessionBlobTest {

    @Test
    fun `encode and decode round-trip with exact session bytes`() {
        val session = byteArrayOf(1, 2, 3, 0x7F)
        val blob = SessionBlob.encode(GameId("tango"), session)

        val decoded = SessionBlob.decode(blob)
        assertEquals(GameId("tango"), decoded?.game)
        assertArrayEquals(session, decoded?.session)
    }

    @Test
    fun `empty session payloads survive the envelope`() {
        val blob = SessionBlob.encode(GameId("zip"), ByteArray(0))
        val decoded = SessionBlob.decode(blob)
        assertEquals(GameId("zip"), decoded?.game)
        assertEquals(0, decoded?.session?.size)
    }

    @Test
    fun `bytes this format never authored decode to null`() {
        assertNull(SessionBlob.decode(ByteArray(0)))
        assertNull(SessionBlob.decode(byteArrayOf(1))) // too short
        assertNull(SessionBlob.decode(byteArrayOf(2, 3, 'x'.code.toByte()))) // wrong version
        assertNull(SessionBlob.decode(byteArrayOf(1, 0, 'x'.code.toByte()))) // empty name
        assertNull(SessionBlob.decode(byteArrayOf(1, 99, 'x'.code.toByte()))) // name runs past end
    }

    @Test
    fun `a non-ascii name is never accepted`() {
        val blob = byteArrayOf(1, 2, 0xC3.toByte(), 0xA9.toByte()) // "é"
        assertNull(SessionBlob.decode(blob))
    }
}
