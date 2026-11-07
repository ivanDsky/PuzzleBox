package com.ivandsky.puzzlebox.games.strands.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.tinyPuzzle
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsSessionCodecTest {

    private val day = LocalDate.of(2026, 11, 5)
    private val real = StrandsFixtures.puzzle(day)

    private val midGame = StrandsSession(
        ref = StrandsPuzzleRef(PuzzleKey.Daily(day, StrandsNumber.of(day))),
        state = StrandsState(
            puzzle = real,
            found = listOf("WIND", "GIVEMEABREAK", "BREAD"),
            nonTheme = linkedSetOf("RAGE", "DIME", "BRAG", "AHEM"),
            hintsUsed = 2,
            activeHint = ActiveHint("FAITH", HintStage.OrderRevealed),
        ),
        elapsedMs = 987_654_321L,
    )

    private fun roundTrip(session: StrandsSession): StrandsSession =
        StrandsSessionCodec.deserialize(StrandsSessionCodec.serialize(session)).getOrNull()!!

    @Test
    fun `every A30 field round-trips`() {
        val restored = roundTrip(midGame)
        assertEquals(midGame, restored)
        assertEquals(midGame.state.found, restored.state.found)
        assertEquals(midGame.state.nonTheme.toList(), restored.state.nonTheme.toList())
        assertEquals(2, restored.state.hintsUsed)
        assertEquals(ActiveHint("FAITH", HintStage.OrderRevealed), restored.state.activeHint)
        assertEquals(987_654_321L, restored.elapsedMs)
        assertEquals(StrandsPuzzleRef(PuzzleKey.Daily(day, 977)), restored.ref)
    }

    @Test
    fun `serialization is canonical`() {
        val bytes = StrandsSessionCodec.serialize(midGame)
        assertArrayEquals(bytes, StrandsSessionCodec.serialize(roundTrip(midGame)))
    }

    @Test
    fun `fresh session without ref or hint round-trips`() {
        val fresh = StrandsSession(ref = null, state = StrandsState(tinyPuzzle))
        assertEquals(fresh, roundTrip(fresh))
    }

    @Test
    fun `ref without a number round-trips`() {
        val session = StrandsSession(ref = StrandsPuzzleRef(PuzzleKey.Daily(day, null)), state = StrandsState(tinyPuzzle))
        assertEquals(session, roundTrip(session))
    }

    @Test
    fun `every truncation is malformed`() {
        val tinyState = StrandsState(
            puzzle = tinyPuzzle,
            found = listOf("CAT"),
            nonTheme = setOf("TAT"),
            hintsUsed = 1,
            activeHint = ActiveHint("ATS", HintStage.Outlined),
        )
        val bytes = StrandsSessionCodec.serialize(midGame.copy(state = tinyState))
        for (len in 0 until bytes.size) {
            val result = StrandsSessionCodec.deserialize(bytes.copyOf(len))
            assertTrue("prefix $len: $result", (result as DecodeResult.Err).error is DecodeError.Malformed)
        }
    }

    @Test
    fun `both key kinds round-trip`() {
        assertEquals(PuzzleKey.Daily(day, 977), roundTrip(midGame).ref?.key)
        val custom = midGame.copy(ref = StrandsPuzzleRef(PuzzleKey.Custom("ABC234")))
        val restored = roundTrip(custom)
        assertEquals(custom, restored)
        assertEquals(PuzzleKey.Custom("ABC234"), restored.ref?.key)
    }

    @Test
    fun `the ref carries a key tag right after the flags byte`() {
        val fresh = StrandsState(tinyPuzzle)
        val at = 1 + StrandsSessionCodec.serialize(StrandsSession(null, fresh)).indexOfFlags()
        val daily = StrandsSessionCodec.serialize(StrandsSession(StrandsPuzzleRef(PuzzleKey.Daily(day, null)), fresh))
        assertEquals(0, daily[at].toInt())
        val custom = StrandsSessionCodec.serialize(StrandsSession(StrandsPuzzleRef(PuzzleKey.Custom("ABC234")), fresh))
        val tail = listOf(1, 6) + "ABC234".map { it.code }
        assertEquals(tail, custom.copyOfRange(at, at + tail.size).map(Byte::toInt))
    }

    /** Index of the flags byte: version + varint-length puzzle blob (tiny puzzle: 1-byte length). */
    private fun ByteArray.indexOfFlags(): Int = 2 + this[1].toInt()

    @Test
    fun `a custom ref with a number flag is malformed`() {
        val fresh = StrandsState(tinyPuzzle)
        val bytes = StrandsSessionCodec.serialize(StrandsSession(StrandsPuzzleRef(PuzzleKey.Custom("ABC234")), fresh))
        val flagsAt = bytes.indexOfFlags()
        bytes[flagsAt] = (bytes[flagsAt].toInt() or 2).toByte()
        assertTrue((StrandsSessionCodec.deserialize(bytes) as DecodeResult.Err).error is DecodeError.Malformed)
    }

    @Test
    fun `an empty custom id is malformed`() {
        val fresh = StrandsState(tinyPuzzle)
        val bytes = StrandsSessionCodec.serialize(StrandsSession(StrandsPuzzleRef(PuzzleKey.Custom("A")), fresh))
        val lenAt = bytes.indexOfFlags() + 2
        assertEquals(1, bytes[lenAt].toInt())
        bytes[lenAt] = 0
        assertTrue((StrandsSessionCodec.deserialize(bytes) as DecodeResult.Err).error is DecodeError.Malformed)
    }

    @Test
    fun `a custom id that is not UTF-8 is malformed`() {
        val fresh = StrandsState(tinyPuzzle)
        val bytes = StrandsSessionCodec.serialize(StrandsSession(StrandsPuzzleRef(PuzzleKey.Custom("A")), fresh))
        bytes[bytes.indexOfFlags() + 3] = 0xFF.toByte()
        assertTrue((StrandsSessionCodec.deserialize(bytes) as DecodeResult.Err).error is DecodeError.Malformed)
    }

    // ------------------------------------------------------------- frozen fixtures

    private fun fixture(name: String): ByteArray =
        StrandsSessionCodecTest::class.java.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    /** The session both fixtures froze, minus the ref. */
    private fun frozen(ref: StrandsPuzzleRef) = StrandsSession(
        ref = ref,
        state = StrandsState(tinyPuzzle, listOf("CAT"), linkedSetOf("TAT"), 1, ActiveHint("ATS", HintStage.Outlined)),
        elapsedMs = 42_000L,
    )

    @Test
    fun `frozen v1 daily save decodes as a Daily key`() {
        val bytes = fixture("strands-session-v1.bin")
        assertEquals(1, bytes[0].toInt())
        val restored = StrandsSessionCodec.deserialize(bytes).getOrNull()!!
        assertEquals(PuzzleKey.Daily(day, 977), restored.ref?.key)
        assertEquals(frozen(StrandsPuzzleRef(PuzzleKey.Daily(day, 977))), restored)
    }

    @Test
    fun `frozen v2 custom save decodes as a Custom key`() {
        val bytes = fixture("strands-session-v2-custom.bin")
        assertEquals(2, bytes[0].toInt())
        val restored = StrandsSessionCodec.deserialize(bytes).getOrNull()!!
        assertEquals(PuzzleKey.Custom("ABC234"), restored.ref?.key)
        assertEquals(frozen(StrandsPuzzleRef(PuzzleKey.Custom("ABC234"))), restored)
        assertArrayEquals(bytes, StrandsSessionCodec.serialize(restored))
    }

    @Test
    fun `version 3 and beyond are unsupported`() {
        assertEquals(2, StrandsSessionCodec.VERSION)
        for (version in listOf(0, 3, 99)) {
            val bytes = StrandsSessionCodec.serialize(midGame).also { it[0] = version.toByte() }
            assertEquals(
                DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = 2)),
                StrandsSessionCodec.deserialize(bytes),
            )
        }
    }

    @Test
    fun `a found word outside the puzzle is malformed`() {
        val bytes = StrandsSessionCodec.serialize(
            StrandsSession(ref = null, state = StrandsState(tinyPuzzle, found = listOf("TAT"))),
        )
        assertTrue((StrandsSessionCodec.deserialize(bytes) as DecodeResult.Err).error is DecodeError.Malformed)
    }
}
