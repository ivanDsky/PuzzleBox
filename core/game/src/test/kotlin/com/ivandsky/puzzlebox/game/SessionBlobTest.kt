package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The self-describing relaunch carrier (ADR-0005): encode/decode round-trips, corrupt payloads decode to null — never guessed through. */
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

    private fun v2(name: String, sessionLen: Long, session: ByteArray, log: ByteArray): ByteArray =
        byteArrayOf(2, name.length.toByte()) + name.toByteArray(Charsets.US_ASCII) +
            ByteArray(4) { (sessionLen shr (24 - 8 * it)).toByte() } + session + log

    @Test
    fun `hand-built version 2 bytes decode to session and log`() {
        val session = byteArrayOf(9, 8, 7)
        val log = byteArrayOf(5, 6)
        val decoded = SessionBlob.decode(v2("tango", 3, session, log))

        assertEquals(GameId("tango"), decoded?.game)
        assertArrayEquals(session, decoded?.session)
        assertArrayEquals(log, decoded?.log)
    }

    @Test
    fun `version 2 with an empty session and a one byte log decodes`() {
        val decoded = SessionBlob.decode(v2("zip", 0, ByteArray(0), byteArrayOf(1)))
        assertEquals(0, decoded?.session?.size)
        assertArrayEquals(byteArrayOf(1), decoded?.log)
    }

    @Test
    fun `version 1 decodes with a null log`() {
        assertNull(SessionBlob.decode(SessionBlob.encode(GameId("zip"), byteArrayOf(1)))?.log)
    }

    @Test
    fun `truncated version 2 bytes decode to null`() {
        assertNull(SessionBlob.decode(byteArrayOf(2, 1, 'x'.code.toByte(), 0, 0))) // length cut short
        assertNull(SessionBlob.decode(v2("tango", 4, byteArrayOf(1, 2, 3), byteArrayOf(5)).copyOf(12))) // session runs past end
        assertNull(SessionBlob.decode(v2("tango", 9, byteArrayOf(1), byteArrayOf(5)))) // length past buffer
        assertNull(SessionBlob.decode(v2("tango", 0xFFFFFFFFL, byteArrayOf(1), byteArrayOf(5)))) // huge length
    }

    @Test
    fun `version 2 with an empty log decodes to null`() {
        assertNull(SessionBlob.decode(v2("tango", 3, byteArrayOf(1, 2, 3), ByteArray(0))))
    }

    // Captured from the version 1 encoder before the log parameter existed; never regenerate from SessionBlob.
    private val v1TangoFixture = byteArrayOf(
        1, 5, 0x74, 0x61, 0x6E, 0x67, 0x6F, // version, "tango"
        1, 2, 3, 0x7F, // session
    )

    @Test
    fun `encode without a log is byte-identical to the version 1 fixture`() {
        val session = byteArrayOf(1, 2, 3, 0x7F)

        assertArrayEquals(v1TangoFixture, SessionBlob.encode(GameId("tango"), session))
        assertArrayEquals(v1TangoFixture, SessionBlob.encode(GameId("tango"), session, null))
    }

    @Test
    fun `an empty log is treated as no log and stays version 1`() {
        val session = byteArrayOf(1, 2, 3, 0x7F)

        assertArrayEquals(v1TangoFixture, SessionBlob.encode(GameId("tango"), session, ByteArray(0)))
    }

    @Test
    fun `encode with a log writes the version 2 fixture`() {
        val fixture = byteArrayOf(
            2, 5, 0x74, 0x61, 0x6E, 0x67, 0x6F, // version, "tango"
            0, 0, 0, 4, // sessionLen, big-endian
            1, 2, 3, 0x7F, // session
            5, 6, // log
        )

        assertArrayEquals(fixture, SessionBlob.encode(GameId("tango"), byteArrayOf(1, 2, 3, 0x7F), byteArrayOf(5, 6)))
    }

    @Test
    fun `version 2 round-trips session and log through decode`() {
        val session = byteArrayOf(1, 2, 3, 0x7F)
        val log = byteArrayOf(5, 6, 0, 0xFF.toByte())

        val decoded = SessionBlob.decode(SessionBlob.encode(GameId("tango"), session, log))

        assertEquals(GameId("tango"), decoded?.game)
        assertArrayEquals(session, decoded?.session)
        assertArrayEquals(log, decoded?.log)
    }

    @Test
    fun `version 2 round-trips an empty session with a one byte log`() {
        val decoded = SessionBlob.decode(SessionBlob.encode(GameId("zip"), ByteArray(0), byteArrayOf(1)))

        assertEquals(GameId("zip"), decoded?.game)
        assertEquals(0, decoded?.session?.size)
        assertArrayEquals(byteArrayOf(1), decoded?.log)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `version 2 still rejects an empty game id`() {
        SessionBlob.encode(GameId(""), byteArrayOf(1), byteArrayOf(1))
    }
}
