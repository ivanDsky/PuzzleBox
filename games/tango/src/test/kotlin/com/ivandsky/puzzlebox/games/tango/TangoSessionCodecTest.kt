package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoSessionCodecTest {

    private val session = TangoSession(
        ref = TangoPuzzleRef(PuzzleKey.Daily(LocalDate.of(2026, 11, 2), 42)),
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
        val bytes = TangoSessionCodec.serialize(session).clone().also { it[0] = 4 }
        assertEquals(
            DecodeError.UnsupportedVersion(found = 4, maxKnown = 3),
            (TangoSessionCodec.deserialize(bytes) as DecodeResult.Err).error,
        )
    }

    // ---- v3: the ref carries a PuzzleKey ----

    private val fixtureHistory = CommandHistory<TangoPlacements, TangoPlace>(
        done = listOf(TangoPlace(2, TangoCell.MOON, null), TangoPlace(15, TangoCell.SUN, null)),
        undone = listOf(TangoPlace(9, TangoCell.MOON, null)),
    )

    /** The session both frozen fixtures were cut from; they differ only in the ref. */
    private val fixtureSession = session.copy(history = fixtureHistory)

    private val customRef = TangoPuzzleRef(PuzzleKey.Custom("7K3QPX"))

    private fun fixtureBytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { name }.readBytes()

    @Test
    fun `a frozen v2 session decodes as a daily`() {
        val restored = (decode(fixtureBytes("tango-session-v2.bin")) as DecodeResult.Ok).value

        assertEquals(PuzzleKey.Daily(LocalDate.of(2026, 11, 2), 42), restored.ref?.key)
        assertEquals(fixtureSession, restored)
    }

    @Test
    fun `a frozen v3 custom session decodes as a custom key`() {
        val restored = (decode(fixtureBytes("tango-session-v3-custom.bin")) as DecodeResult.Ok).value

        assertEquals(customRef, restored.ref)
        assertEquals(fixtureSession.copy(ref = customRef), restored)
    }

    @Test
    fun `v3 encodes the custom fixture byte for byte`() {
        val bytes = TangoSessionCodec.serialize(fixtureSession.copy(ref = customRef))

        assertEquals(fixtureBytes("tango-session-v3-custom.bin").toList(), bytes.toList())
    }

    @Test
    fun `both key kinds round-trip`() {
        val refs = listOf(
            TangoPuzzleRef(PuzzleKey.Daily(LocalDate.of(2026, 11, 2), 42)),
            TangoPuzzleRef(PuzzleKey.Daily(LocalDate.of(2026, 11, 3), null)),
            customRef,
        )
        for (ref in refs) {
            val restored = (decode(TangoSessionCodec.serialize(fixtureSession.copy(ref = ref))) as DecodeResult.Ok).value
            assertEquals(ref.key, restored.ref?.key)
            assertEquals(fixtureSession.copy(ref = ref), restored)
        }
    }

    @Test
    fun `an unknown key tag is malformed`() {
        val bytes = TangoSessionCodec.serialize(session)
        val tagAt = 3 + ((bytes[1].toInt() and 0xFF) shl 8) + (bytes[2].toInt() and 0xFF) + 1

        assertTrue(decode(bytes.clone().also { it[tagAt] = 2 }) is DecodeResult.Err)
    }

    @Test
    fun `a truncated custom id is malformed`() {
        val bytes = fixtureBytes("tango-session-v3-custom.bin")
        val tagAt = 3 + ((bytes[1].toInt() and 0xFF) shl 8) + (bytes[2].toInt() and 0xFF) + 1

        assertTrue(decode(bytes.copyOf(tagAt + 4)) is DecodeResult.Err)
    }

    /** [session]'s daily bytes at a pre-v3 [version]: the v3 layout minus the key tag. */
    private fun legacyBytes(version: Int): ByteArray {
        val v3 = TangoSessionCodec.serialize(session)
        val tagAt = 3 + ((v3[1].toInt() and 0xFF) shl 8) + (v3[2].toInt() and 0xFF) + 1
        return (v3.copyOfRange(0, tagAt) + v3.copyOfRange(tagAt + 1, v3.size)).also { it[0] = version.toByte() }
    }

    // ---- v2: undo/redo history ----

    private val history = CommandHistory<TangoPlacements, TangoPlace>(
        done = listOf(
            TangoPlace(2, TangoCell.MOON, previous = null),
            TangoPlace(15, TangoCell.SUN, previous = null),
            TangoPlace(15, null, previous = TangoCell.SUN),
        ),
        undone = listOf(
            TangoPlace(4, TangoCell.SUN, previous = TangoCell.MOON),
            TangoPlace(9, TangoCell.MOON, previous = null),
        ),
    )

    private fun decode(bytes: ByteArray) = TangoSessionCodec.deserialize(bytes)

    @Test
    fun `history round-trips with undo and redo stacks in order`() {
        val withHistory = session.copy(history = history)

        val restored = (decode(TangoSessionCodec.serialize(withHistory)) as DecodeResult.Ok).value

        assertEquals(history, restored.history)
        assertEquals(withHistory.copy(unknownTail = ByteArray(0)), restored)
    }

    @Test
    fun `a restored session can keep undoing and redoing`() {
        val board = mapOf(2 to TangoCell.MOON, 4 to TangoCell.MOON)
        val live = session.copy(
            placements = board,
            history = CommandHistory(
                done = listOf(TangoPlace(2, TangoCell.MOON, null), TangoPlace(4, TangoCell.MOON, null)),
            ),
        ).withUndo()

        val restored = (decode(TangoSessionCodec.serialize(live)) as DecodeResult.Ok).value

        assertEquals(mapOf(2 to TangoCell.MOON), restored.placements)
        assertEquals(board, restored.withRedo().placements)
    }

    @Test
    fun `a v1 blob still decodes with an empty history`() {
        // v1 = v2 minus the two u16 history counts (no tail here, so they are the last 4 bytes).
        val v2 = legacyBytes(2)
        val v1 = v2.copyOfRange(0, v2.size - 4).also { it[0] = 1 }

        val restored = (decode(v1) as DecodeResult.Ok).value

        assertEquals(session, restored)
        assertEquals(CommandHistory<TangoPlacements, TangoPlace>(), restored.history)
    }

    @Test
    fun `a v1 unknown tail survives and is not mistaken for history`() {
        val v2 = legacyBytes(2)
        val v1 = v2.copyOfRange(0, v2.size - 4).also { it[0] = 1 } + byteArrayOf(7, 8, 9)

        val restored = (decode(v1) as DecodeResult.Ok).value

        assertTrue(restored.unknownTail.contentEquals(byteArrayOf(7, 8, 9)))
        assertFalse(restored.history.canUndo)
    }

    @Test
    fun `unknown tail survives after the history`() {
        val both = session.copy(history = history, unknownTail = byteArrayOf(4, 5))

        val restored = (decode(TangoSessionCodec.serialize(both)) as DecodeResult.Ok).value

        assertEquals(history, restored.history)
        assertTrue(restored.unknownTail.contentEquals(byteArrayOf(4, 5)))
    }

    @Test
    fun `a history longer than the cap is corrupt`() {
        val bytes = TangoSessionCodec.serialize(session) // empty history: [.., 0,0 (done), 0,0 (undone)]
        val count = TangoSessionCodec.MAX_HISTORY + 1
        val oversized = bytes.copyOf(bytes.size - 4) + byteArrayOf((count shr 8).toByte(), count.toByte()) +
            ByteArray(count * 3) + byteArrayOf(0, 0)

        assertTrue(decode(oversized) is DecodeResult.Err)
    }

    @Test
    fun `serialize keeps only the newest commands past the cap`() {
        val flips = List(TangoSessionCodec.MAX_HISTORY + 5) { n ->
            if (n % 2 == 0) TangoPlace(2, TangoCell.MOON, null) else TangoPlace(2, null, TangoCell.MOON)
        }

        val restored = (decode(TangoSessionCodec.serialize(session.copy(history = CommandHistory(done = flips)))) as DecodeResult.Ok).value

        assertEquals(TangoSessionCodec.MAX_HISTORY, restored.history.done.size)
        assertEquals(flips.takeLast(TangoSessionCodec.MAX_HISTORY), restored.history.done)
    }

    @Test
    fun `corrupt history commands are malformed`() {
        val base = TangoSessionCodec.serialize(session.copy(history = CommandHistory(done = listOf(TangoPlace(2, TangoCell.MOON, null)))))
        // Layout tail: [0,1 count][idx,value,previous][0,0 undone count].
        val cmd = base.size - 5

        fun corrupt(mutate: (ByteArray) -> Unit) =
            decode(base.clone().also(mutate)) is DecodeResult.Err

        assertTrue("index out of range", corrupt { it[cmd] = 36 })
        assertTrue("unknown cell byte", corrupt { it[cmd + 1] = 7 })
        assertTrue("no-op command", corrupt { it[cmd + 2] = it[cmd + 1] })
        assertTrue("truncated", decode(base.copyOf(base.size - 3)) is DecodeResult.Err)
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
