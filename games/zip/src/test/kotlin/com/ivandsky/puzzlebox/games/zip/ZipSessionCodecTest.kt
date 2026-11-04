package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session codec v2: the §4 reader rules mirrored for the play state — exact round trip
 * (C1) including the undo/redo history, pinned layout (C2), determinism (C3), unknown-tail
 * preservation (A19/D-3), version guard (C5), v1 saves still readable, and corrupt history
 * rejected.
 */
class ZipSessionCodecTest {

    private val started = Instant.ofEpochMilli(1_700_000_000_000)
    private val completed = started.plusSeconds(321)

    private fun session(completedAt: Instant? = null, ref: ZipPuzzleRef? = ref()) = ZipSession(
        ref = ref,
        puzzle = ZipTestSupport.n5Puzzle(),
        path = ZipTestSupport.path(0, 1, 2),
        hintsUsed = 2,
        mistakeCount = 3,
        backtrackCount = 1,
        elapsedMs = 87_000,
        timerStarted = true,
        runningSince = started, // NEVER persisted
        completedAt = completedAt,
    )

    private fun ref() = ZipPuzzleRef(java.time.LocalDate.of(2026, 10, 30), 592)

    /** C1: deserialize∘serialize == identity for the persisted shape. */
    @Test
    fun `C1 - session round trip preserves every persisted field`() {
        val blob = ZipSessionCodec.serialize(session())
        val restored = (ZipSessionCodec.deserialize(blob) as DecodeResult.Ok).value
        // runningSince is a runtime anchor, never persisted: restored comes back paused.
        assertEquals(session().copy(runningSince = null), restored)
        assertNull(restored.runningSince)
    }

    @Test
    fun `C1 - completed sessions round trip`() {
        val blob = ZipSessionCodec.serialize(session(completedAt = completed))
        val restored = (ZipSessionCodec.deserialize(blob) as DecodeResult.Ok).value
        assertEquals(completed, restored.completedAt)
        assertEquals(87_000, restored.elapsedMs)
    }

    /** C2: the layout, pinned: version, u16 puzzle length, puzzle bytes, flags, ref, path, counters. */
    @Test
    fun `C2 - byte layout matches the section-4 table`() {
        val puzzleBytes = ZipCodec.encode(ZipTestSupport.n5Puzzle())
        val blob = ZipSessionCodec.serialize(
            session().copy(path = ZipTestSupport.path(0, 1), timerStarted = false),
        )
        val expected = buildList {
            add(2) // version
            add((puzzleBytes.size shr 8) and 0xFF); add(puzzleBytes.size and 0xFF)
            addAll(puzzleBytes.map(Byte::toInt))
            add(2) // flags: bit1 ref present, timerStarted false
            // ref: epochDay, numberPresent, number
            addAll(java.time.LocalDate.of(2026, 10, 30).toEpochDay().let { v ->
                (0 until 8).map { ((v shr (it * 8)) and 0xFF).toInt() }
            })
            add(1) // number present
            addAll(592L.let { v -> (0 until 8).map { ((v shr (it * 8)) and 0xFF).toInt() } })
            add(2) // pathLen
            add(0); add(1)
            add(2); add(3); add(1) // hints, mistakes, backtracks
            addAll(87_000L.let { v -> (0 until 8).map { ((v shr (it * 8)) and 0xFF).toInt() } })
            // no completedAt bytes
            add(0); add(0) // doneLen
            add(0); add(0) // undoneLen
        }.map(Int::toByte).toByteArray()
        assertArrayEquals(expected, blob)
    }

    /** C3: canonical serialization — same session, same bytes. */
    @Test
    fun `C3 - serialization is deterministic`() {
        val s = session()
        assertArrayEquals(ZipSessionCodec.serialize(s), ZipSessionCodec.serialize(s))
    }

    @Test
    fun `ref absence is a flag, not a sentinel`() {
        val blob = ZipSessionCodec.serialize(session(ref = null))
        val restored = (ZipSessionCodec.deserialize(blob) as DecodeResult.Ok).value
        assertNull(restored.ref)
    }

    @Test
    fun `timerStarted bit survives, running anchor does not`() {
        val paused = session().copy(runningSince = null)
        val blob = ZipSessionCodec.serialize(paused)
        val restored = (ZipSessionCodec.deserialize(blob) as DecodeResult.Ok).value
        assertTrue(restored.timerStarted)
        assertNull(restored.runningSince)
        // a restored session resumes on foreground without double-counting
        val resumed = restored.onForeground(started.plusSeconds(10))
        assertEquals(87_000, restored.displayedElapsedMs(started.plusSeconds(10)))
        assertEquals(97_000, resumed.displayedElapsedMs(started.plusSeconds(20)))
    }

    /** C5: version guard. */
    @Test
    fun `C5 - unknown session version is UnsupportedVersion`() {
        val blob = ZipSessionCodec.serialize(session()).clone().also { it[0] = 7 }
        val error = (ZipSessionCodec.deserialize(blob) as DecodeResult.Err).error
        assertTrue(error is DecodeError.UnsupportedVersion)
        assertEquals(7, (error as DecodeError.UnsupportedVersion).found)
    }

    /** v1 counters are u8 fields: serialize is fail-fast, never silent truncation. */
    @Test
    fun `counters beyond u8 are rejected on serialize`() {
        val overflow = 256
        assertThrows(IllegalArgumentException::class.java) {
            ZipSessionCodec.serialize(session().copy(hintsUsed = overflow))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ZipSessionCodec.serialize(session().copy(mistakeCount = overflow))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ZipSessionCodec.serialize(session().copy(backtrackCount = overflow))
        }
        // the u8 ceiling itself round trips
        val ceiling = session().copy(hintsUsed = 255, mistakeCount = 255, backtrackCount = 255)
        val restored = (ZipSessionCodec.deserialize(ZipSessionCodec.serialize(ceiling)) as DecodeResult.Ok).value
        assertEquals(ceiling.copy(runningSince = null), restored)
    }

    @Test
    fun `truncated and oversized blobs are Malformed`() {
        val full = ZipSessionCodec.serialize(session())
        for (cut in 0 until full.size) {
            val result = ZipSessionCodec.deserialize(full.copyOf(cut))
            assertTrue("cut=$cut", result is DecodeResult.Err)
            assertTrue((result as DecodeResult.Err).error is DecodeError.Malformed)
        }
        // trailing bytes: an unknown extension is carried, never rejected (A19/D-3)
        val trailing = full + byteArrayOf(1)
        val extended = ZipSessionCodec.deserialize(trailing)
        assertTrue(extended is DecodeResult.Ok)
        assertArrayEquals(byteArrayOf(1), (extended as DecodeResult.Ok).value.unknownTail)
        assertTrue(ZipSessionCodec.deserialize(ByteArray(0)) is DecodeResult.Err)
    }

    /** D-3/A19: a tail appended by a newer version survives decode and re-emits verbatim. */
    @Test
    fun `unknown session tail is preserved and re-emitted`() {
        val blob = ZipSessionCodec.serialize(session()) + byteArrayOf(9, 8, 7)
        val restored = (ZipSessionCodec.deserialize(blob) as DecodeResult.Ok).value
        assertArrayEquals(byteArrayOf(9, 8, 7), restored.unknownTail)
        assertArrayEquals(blob, ZipSessionCodec.serialize(restored))
    }

    /** D-5: counters that would silently truncate at u8 fail fast instead. */
    @Test
    fun `u8 counters above 255 fail fast instead of truncating`() {
        fun trySerialize(build: (ZipSession) -> ZipSession) =
            runCatching { ZipSessionCodec.serialize(build(session())) }
        assertTrue(trySerialize { it.copy(hintsUsed = 256) }.isFailure)
        assertTrue(trySerialize { it.copy(mistakeCount = 256) }.isFailure)
        assertTrue(trySerialize { it.copy(backtrackCount = 256) }.isFailure)
        assertTrue(trySerialize { it.copy(hintsUsed = 255, mistakeCount = 255, backtrackCount = 255) }.isSuccess)
    }

    /** A corrupted embedded puzzle poisons the session blob (the puzzle rides by value). */
    @Test
    fun `corrupt embedded puzzle is rejected`() {
        val full = ZipSessionCodec.serialize(session())
        // find the puzzle n byte: version(1) + len(2) + version(1) + keyLen(1) + key(6) = offset 11
        val corrupted = full.clone().also { it[11] = 9 } // n=9 != variant n5
        val result = ZipSessionCodec.deserialize(corrupted)
        assertTrue(result is DecodeResult.Err)
    }

    private val t0 = Instant.ofEpochMilli(1_000_000)

    /** All three command kinds on the stack, one of them undone: the redo stack travels too. */
    private fun playedSession(): ZipSession {
        var s = ZipSession(ref = ref(), puzzle = ZipTestSupport.n5Puzzle())
        listOf(0, 1, 2, 3, 4).forEach { s = s.withMove(ZipMove.Extend(Cell(it)), t0) }
        s = s.withMove(ZipMove.RetractTo(Cell(2)), t0)
        s = s.withMove(ZipMove.Extend(Cell(3)), t0)
        s = s.withMove(ZipMove.Reset, t0)
        return s.withUndo()
    }

    @Test
    fun `C1 - history round trips with done and redo stacks`() {
        val played = playedSession()
        assertTrue(played.history.canUndo)
        assertTrue(played.history.canRedo)
        val restored = (ZipSessionCodec.deserialize(ZipSessionCodec.serialize(played)) as DecodeResult.Ok).value
        assertEquals(played.copy(runningSince = null), restored)
        // the restored history is live: redo re-applies the reset
        assertEquals(emptyList<Cell>(), restored.withRedo().path)
    }

    /** v1 saves (no history sections) must keep restoring: empty history, same session. */
    @Test
    fun `v1 blobs decode with an empty history and keep their tail`() {
        val s = session().copy(runningSince = null)
        val v2 = ZipSessionCodec.serialize(s)
        val v1 = v2.copyOf(v2.size - 4).also { it[0] = 1 } // drop the two u16 history lengths
        assertEquals(s, (ZipSessionCodec.deserialize(v1) as DecodeResult.Ok).value)

        val withTail = (ZipSessionCodec.deserialize(v1 + byteArrayOf(9, 8)) as DecodeResult.Ok).value
        assertArrayEquals(byteArrayOf(9, 8), withTail.unknownTail)
        // re-saving upgrades to v2 and keeps the tail after the history sections
        val upgraded = ZipSessionCodec.deserialize(ZipSessionCodec.serialize(withTail)) as DecodeResult.Ok
        assertArrayEquals(byteArrayOf(9, 8), upgraded.value.unknownTail)
    }

    @Test
    fun `a history that does not reproduce the path is Malformed`() {
        val forged = session().copy(
            history = CommandHistory(done = listOf(ZipCommand.Extend(Cell(7)))), // path is 0,1,2
        )
        val result = ZipSessionCodec.deserialize(ZipSessionCodec.serialize(forged))
        assertTrue(result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }

    @Test
    fun `history commands outside the board, unknown tags and oversized counts are Malformed`() {
        fun decodeWithHistory(vararg history: Int): DecodeResult<ZipSession> {
            val base = ZipSessionCodec.serialize(session())
            val blob = base.copyOf(base.size - 4) + history.map(Int::toByte).toByteArray()
            return ZipSessionCodec.deserialize(blob)
        }
        // doneLen=1, Extend(cell 25) on a 25-cell board
        assertTrue(decodeWithHistory(0, 1, 0, 25, 0, 0) is DecodeResult.Err)
        // doneLen=1, unknown tag 9
        assertTrue(decodeWithHistory(0, 1, 9, 0, 0, 0) is DecodeResult.Err)
        // doneLen = 10_001 (> MAX_HISTORY), with no bytes behind it
        assertTrue(decodeWithHistory(0x27, 0x11, 0, 0) is DecodeResult.Err)
        // a RetractTo whose tail runs past the blob
        assertTrue(decodeWithHistory(0, 1, 1, 2, 5, 3) is DecodeResult.Err)
    }

    @Test
    fun `serializing more than MAX_HISTORY commands fails fast`() {
        val tooMany = session().copy(
            history = CommandHistory(done = List(ZipSessionCodec.MAX_HISTORY + 1) { ZipCommand.Extend(Cell(2)) }),
        )
        assertThrows(IllegalArgumentException::class.java) { ZipSessionCodec.serialize(tooMany) }
    }

    /** Deterministic across serialize-decode-encode-decode (no hidden state). */
    @Test
    fun `nested puzzle blob is canonical`() {
        val s = session()
        val first = ZipSessionCodec.serialize(s)
        val restored = (ZipSessionCodec.deserialize(first) as DecodeResult.Ok).value
        val second = ZipSessionCodec.serialize(restored)
        assertArrayEquals(first, second)
    }
}
