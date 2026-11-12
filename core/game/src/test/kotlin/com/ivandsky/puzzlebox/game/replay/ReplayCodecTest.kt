package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.random.Random

class ReplayCodecTest {

    private val golden = record(
        listOf(move(130, kind = 2, -1), move(130, kind = 31, 0, 1, 2, 3, 4, 5, 6)),
        outcome = ReplayOutcome.Solved,
        endMs = 300,
    )

    private val goldenLayout = bytes(
        0x03, 'z'.code, 'i'.code, 'p'.code,
        0x01,
        0xAC, 0x02,
        0x02,
        0x82, 0x01, 0x11, 0x01,
        0x00, 0xFF, 0x07, 0x00, 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C,
    )

    @Test
    fun `a zero-move record round trips`() = assertRoundTrips(record())

    @Test
    fun `a one-move record round trips`() = assertRoundTrips(record(listOf(move(1_250, kind = 3, 4, 7))))

    @Test
    fun `a 4 096-move record round trips`() {
        val moves = List(ReplayCodec.MAX_MOVES) { i -> move(i * 137L, kind = i % 32, i % 81, -(i % 9)) }
        assertRoundTrips(record(moves, endMs = ReplayCodec.MAX_MOVES * 137L))
    }

    @Test
    fun `negative args round trip`() =
        assertRoundTrips(record(listOf(move(10, kind = 1, -1, Int.MIN_VALUE, Int.MAX_VALUE, 0, -64, 63))))

    @Test
    fun `argc above 6 round trips`() {
        val seven = move(10, kind = 2, *IntArray(7) { it })
        val most = move(20, kind = 2, *IntArray(ReplayCodec.MAX_ARGS) { -it })
        assertRoundTrips(record(listOf(seven, most)))
    }

    @Test
    fun `every outcome round trips as its own byte`() {
        val outcomeBytes = mapOf(ReplayOutcome.Open to 0, ReplayOutcome.Solved to 1, ReplayOutcome.GaveUp to 2)
        assertEquals(ReplayOutcome.entries.toSet(), outcomeBytes.keys)
        for ((outcome, byte) in outcomeBytes) {
            val sealed = record(outcome = outcome)
            assertRoundTrips(sealed)
            assertEquals(byte, replayLayout(checkNotNull(ReplayCodec.encode(sealed)))[1 + sealed.game.value.length].toInt())
        }
    }

    @Test
    fun `equal stamps and the largest stamp round trip`() {
        val moves = listOf(move(0), move(0), move(Long.MAX_VALUE), move(Long.MAX_VALUE))
        assertRoundTrips(record(moves, endMs = Long.MAX_VALUE))
    }

    @Test
    fun `the stored form is version 1 then the deflated layout`() {
        val stored = checkNotNull(ReplayCodec.encode(golden))
        assertEquals(1, stored[0].toInt())
        assertArrayEquals(goldenLayout, replayLayout(stored))
    }

    @Test
    fun `a hand-written layout decodes to its record`() = assertDecodesTo(golden, storedReplay(goldenLayout))

    @Test
    fun `600 one-cell moves seal under 4 KiB`() {
        val random = Random(600)
        var atMs = 0L
        val moves = List(600) {
            atMs += random.nextLong(150, 5_000)
            move(atMs, kind = 0, random.nextInt(81))
        }
        val stored = checkNotNull(ReplayCodec.encode(record(moves, endMs = atMs)))
        assertTrue("sealed at ${stored.size} bytes", stored.size < 4_096)
        assertNotNull(SolveReplay.of(stored))
    }

    @Test
    fun `4 096 moves are the most a record holds`() {
        val most = record(List(ReplayCodec.MAX_MOVES) { move(0) })
        val onePast = record(List(ReplayCodec.MAX_MOVES + 1) { move(0) })
        assertRoundTrips(most)
        assertDecodesTo(most, storedReplay(layoutOf(most)))
        assertNull(ReplayCodec.encode(onePast))
        assertMalformed(ReplayCodec.decode(storedReplay(layoutOf(onePast))))
    }

    @Test
    fun `64 args are the most a move holds`() {
        val most = record(listOf(move(0, kind = 0, *IntArray(ReplayCodec.MAX_ARGS))))
        val onePast = record(listOf(move(0, kind = 0, *IntArray(ReplayCodec.MAX_ARGS + 1))))
        assertRoundTrips(most)
        assertDecodesTo(most, storedReplay(layoutOf(most)))
        assertNull(ReplayCodec.encode(onePast))
        assertMalformed(ReplayCodec.decode(storedReplay(layoutOf(onePast))))
    }

    @Test
    fun `kinds run from 0 to 31`() {
        assertRoundTrips(record(listOf(move(0, kind = 0), move(0, kind = ReplayCodec.MAX_KIND))))
        assertNull(ReplayCodec.encode(record(listOf(move(0, kind = ReplayCodec.MAX_KIND + 1)))))
        assertNull(ReplayCodec.encode(record(listOf(move(0, kind = -1)))))
    }

    @Test
    fun `32 KiB is the largest layout`() {
        val largest = recordOfLayoutSize(ReplayCodec.MAX_INFLATED_BYTES)
        val onePast = recordOfLayoutSize(ReplayCodec.MAX_INFLATED_BYTES + 1)
        assertEquals(32_768, layoutOf(largest).size)
        assertEquals(32_769, layoutOf(onePast).size)
        assertRoundTrips(largest)
        assertDecodesTo(largest, storedReplay(layoutOf(largest)))
        assertNull(ReplayCodec.encode(onePast))
        assertMalformed(ReplayCodec.decode(storedReplay(layoutOf(onePast))))
    }

    @Test
    fun `a layout far past 32 KiB is Err`() =
        assertMalformed(ReplayCodec.decode(storedReplay(ByteArray(4 * 1024 * 1024))))

    @Test
    fun `stamps that decrease do not encode`() {
        assertNull(ReplayCodec.encode(record(listOf(move(500), move(499)))))
        assertNull(ReplayCodec.encode(record(listOf(move(-1)))))
    }

    @Test
    fun `a negative end time does not encode`() = assertNull(ReplayCodec.encode(record(endMs = -1)))

    @Test
    fun `a game id is 1 to 255 ASCII chars`() {
        assertRoundTrips(record(game = "a".repeat(255)))
        assertNull(ReplayCodec.encode(record(game = "a".repeat(256))))
        assertNull(ReplayCodec.encode(record(game = "")))
        assertNull(ReplayCodec.encode(record(game = "zïp")))
        assertMalformed(ReplayCodec.decode(storedReplay(layout { header(game = "") })))
        assertMalformed(ReplayCodec.decode(storedReplay(layout { header(game = "zïp") })))
    }

    @Test
    fun `empty bytes are Err`() = assertMalformed(ReplayCodec.decode(ByteArray(0)))

    @Test
    fun `a newer format version is UnsupportedVersion`() = assertEquals(
        DecodeResult.Err(DecodeError.UnsupportedVersion(found = 2, maxKnown = 1)),
        ReplayCodec.decode(storedReplay(goldenLayout, version = 2)),
    )

    @Test
    fun `format version 0 is Err`() = assertMalformed(ReplayCodec.decode(storedReplay(goldenLayout, version = 0)))

    @Test
    fun `every truncation of a stored replay is Err`() {
        val stored = storedReplay(goldenLayout)
        for (size in 0 until stored.size) assertMalformed(ReplayCodec.decode(stored.copyOf(size)))
    }

    @Test
    fun `a byte after the deflate stream is Err`() =
        assertMalformed(ReplayCodec.decode(storedReplay(goldenLayout) + 0))

    @Test
    fun `a broken deflate stream is Err`() = assertMalformed(ReplayCodec.decode(bytes(1, 0x07)))

    @Test
    fun `every truncation of a layout is Err`() {
        for (size in 0 until goldenLayout.size) assertMalformed(ReplayCodec.decode(storedReplay(goldenLayout.copyOf(size))))
    }

    @Test
    fun `a byte after the last move is Err`() = assertMalformed(ReplayCodec.decode(storedReplay(goldenLayout + 0)))

    @Test
    fun `an unknown outcome byte is Err`() {
        assertTrue(ReplayCodec.decode(storedReplay(layout { header(outcome = 2) })) is DecodeResult.Ok)
        assertMalformed(ReplayCodec.decode(storedReplay(layout { header(outcome = 3) })))
    }

    @Test
    fun `a delta past 63 bits is Err`() {
        val widest = layout { header(moves = 1); varint(Long.MAX_VALUE); write(0) }
        val negative = layout { header(moves = 1); varint(-1); write(0) }
        assertDecodesTo(record(listOf(move(Long.MAX_VALUE)), endMs = 0), storedReplay(widest))
        assertMalformed(ReplayCodec.decode(storedReplay(negative)))
    }

    @Test
    fun `deltas that overflow the stamp are Err`() {
        val latest = layout { header(moves = 2); varint(Long.MAX_VALUE - 1); write(0); varint(1); write(0) }
        val overflow = layout { header(moves = 2); varint(Long.MAX_VALUE); write(0); varint(1); write(0) }
        assertDecodesTo(record(listOf(move(Long.MAX_VALUE - 1), move(Long.MAX_VALUE)), endMs = 0), storedReplay(latest))
        assertMalformed(ReplayCodec.decode(storedReplay(overflow)))
    }

    @Test
    fun `an arg wider than an int is Err`() {
        val widest = layout { header(moves = 1); varint(0); write(1); varint(0xFFFF_FFFFL) }
        val wider = layout { header(moves = 1); varint(0); write(1); varint(0x1_0000_0000L) }
        assertDecodesTo(record(listOf(move(0, kind = 0, Int.MIN_VALUE)), endMs = 0), storedReplay(widest))
        assertMalformed(ReplayCodec.decode(storedReplay(wider)))
    }

    @Test
    fun `an argc that follows is at least 7`() {
        val seven = layout { header(moves = 1); varint(0); write(7); varint(7); repeat(7) { write(0) } }
        val six = layout { header(moves = 1); varint(0); write(7); varint(6); repeat(6) { write(0) } }
        assertDecodesTo(record(listOf(move(0, kind = 0, *IntArray(7))), endMs = 0), storedReplay(seven))
        assertMalformed(ReplayCodec.decode(storedReplay(six)))
    }

    private fun record(
        moves: List<StampedMove> = emptyList(),
        game: String = "zip",
        outcome: ReplayOutcome = ReplayOutcome.Solved,
        endMs: Long = 90_000,
    ) = ReplayRecord(GameId(game), outcome, endMs, moves)

    private fun move(atMs: Long, kind: Int = 0, vararg args: Int) = StampedMove(atMs, ReplayMove(kind, args))

    /** 101 moves of 64 five-byte args come to just under 32 KiB; the game id pads the rest byte by byte. */
    private fun recordOfLayoutSize(size: Int): ReplayRecord {
        val moves = List(101) { move(0, kind = 0, *IntArray(ReplayCodec.MAX_ARGS) { Int.MIN_VALUE }) }
        val unpadded = layoutOf(record(moves, game = "")).size
        return record(moves, game = "a".repeat(size - unpadded))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun layout(build: ByteArrayOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().apply(build).toByteArray()

    private fun ByteArrayOutputStream.header(game: String = "zip", outcome: Int = 1, endMs: Long = 0, moves: Long = 0) {
        write(game.length)
        write(game.toByteArray(Charsets.ISO_8859_1))
        write(outcome)
        varint(endMs)
        varint(moves)
    }

    /** Unsigned LEB128 over all 64 bits, so a negative value writes the ten bytes the codec must refuse. */
    private fun ByteArrayOutputStream.varint(value: Long) {
        var rest = value
        while (rest ushr 7 != 0L) {
            write((rest and 0x7F).toInt() or 0x80)
            rest = rest ushr 7
        }
        write(rest.toInt())
    }

    /** The layout written without any cap, so decode can be shown bytes the encoder refuses to write. */
    private fun layoutOf(record: ReplayRecord): ByteArray = layout {
        header(record.game.value, record.outcome.ordinal, record.endMs, record.moves.size.toLong())
        var previousMs = 0L
        for (stamped in record.moves) {
            varint(stamped.atMs - previousMs)
            previousMs = stamped.atMs
            val args = stamped.move.args
            write((stamped.move.kind shl 3) or minOf(args.size, 7))
            if (args.size >= 7) varint(args.size.toLong())
            for (arg in args) varint(((arg shl 1) xor (arg shr 31)).toLong() and 0xFFFF_FFFFL)
        }
    }

    private fun assertRoundTrips(record: ReplayRecord) {
        val stored = checkNotNull(ReplayCodec.encode(record)) { "encode returned null" }
        assertArrayEquals(layoutOf(record), replayLayout(stored))
        assertDecodesTo(record, stored)
    }

    private fun assertDecodesTo(expected: ReplayRecord, stored: ByteArray) =
        assertEquals(DecodeResult.Ok(expected), ReplayCodec.decode(stored))

    private fun assertMalformed(result: DecodeResult<ReplayRecord>) =
        assertTrue("expected Malformed, got $result", (result as? DecodeResult.Err)?.error is DecodeError.Malformed)
}

/** The stored form of any layout bytes, valid or not: the version byte, then their raw deflate. */
internal fun storedReplay(layout: ByteArray, version: Int = 1): ByteArray {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    deflater.setInput(layout)
    deflater.finish()
    val stored = ByteArrayOutputStream().apply { write(version) }
    val chunk = ByteArray(4_096)
    while (!deflater.finished()) stored.write(chunk, 0, deflater.deflate(chunk))
    deflater.end()
    return stored.toByteArray()
}

/** The layout inside stored bytes the codec wrote. */
internal fun replayLayout(stored: ByteArray): ByteArray {
    val inflater = Inflater(true)
    inflater.setInput(stored, 1, stored.size - 1)
    val layout = ByteArray(2 * ReplayCodec.MAX_INFLATED_BYTES)
    var size = 0
    while (!inflater.finished()) size += inflater.inflate(layout, size, layout.size - size)
    inflater.end()
    return layout.copyOf(size)
}
