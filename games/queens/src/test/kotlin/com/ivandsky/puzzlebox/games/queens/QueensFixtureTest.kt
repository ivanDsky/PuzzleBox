package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * queens-01 DoD end to end on the synthesized official-shape daily fixture
 * (8x8, gridSize/colorGrid/solution per research 02): wire JSON -> QueensPuzzle ->
 * renderer model -> codec bytes -> byte-identical re-encode. Real fixture capture is
 * queens-09's job; this fixture proves the pipeline shape.
 */
class QueensFixtureTest {

    private val fixtureJson: String by lazy {
        QueensFixtureTest::class.java.getResourceAsStream("/fixtures/queens-daily-sample.json")!!
            .readBytes().decodeToString()
    }
    private val date = LocalDate.of(2026, 11, 2)

    private fun parsed(): QueensWireParser.QueensDaily =
        when (val r = QueensWireParser.parse(fixtureJson, date, "1234")) {
            is DecodeResult.Ok -> r.value
            is DecodeResult.Err -> throw AssertionError("fixture parse failed: ${r.error}")
        }

    /** DoD 1: the daily JSON decodes to a QueensPuzzle with the official shape. */
    @Test
    fun `fixture decodes to an 8x8 QueensPuzzle carrying the shipped solution`() {
        val puzzle = parsed().puzzle
        assertEquals(8, puzzle.size)
        assertEquals(64, puzzle.regions.size)
        assertTrue(puzzle.regions.all { it in 0 until 8 })
        assertEquals(8, puzzle.solution!!.size)
        // one queen per row and per column (the shipped official solution)
        assertEquals(8, puzzle.solution!!.map { puzzle.rowOf(it) }.toSet().size)
        assertEquals(8, puzzle.solution!!.map { puzzle.colOf(it) }.toSet().size)
        // one queen per region
        assertEquals(8, puzzle.solution!!.map { puzzle.regionOf(it) }.toSet().size)
        // no adjacency including diagonals (A1)
        puzzle.solution!!.forEach { a ->
            puzzle.solution!!.forEach { b ->
                if (a != b) {
                    assertTrue(
                        "queens $a and $b adjacent",
                        kotlin.math.abs(puzzle.rowOf(a) - puzzle.rowOf(b)) > 1 ||
                            kotlin.math.abs(puzzle.colOf(a) - puzzle.colOf(b)) > 1,
                    )
                }
            }
        }
    }

    /** DoD 2+3: renders via the renderer model and re-encodes byte-identically. */
    @Test
    fun `fixture renders via the renderer model and re-encodes byte-identically`() {
        val puzzle = parsed().puzzle
        val model = QueensRendererModel.of(puzzle, puzzle.solution!!.map { it.idx }.toSet())
        assertEquals(8, model.n)
        assertEquals(puzzle.regions, model.regions)
        assertEquals(puzzle.solution!!.map { it.idx }.toSet(), model.queens)

        val bytes = QueensCodec.encode(puzzle)
        val decoded = QueensCodec.decode(bytes).getOrThrow()
        assertEquals(puzzle, decoded)
        assertArrayEquals(bytes, QueensCodec.encode(decoded))
    }

    /** The envelope path (GameDefinition) agrees with the codec path, byte for byte. */
    @Test
    fun `definition decode-encode round trips the fixture envelope`() {
        val bytes = QueensCodec.encode(parsed().puzzle)
        val envelope = QueensDefinition.decode(bytes).getOrThrow()
        assertEquals(GameId("queens"), envelope.gameId)
        assertEquals(queensVariantId(8), envelope.variant)
        assertEquals(QueensCodec.VERSION, envelope.formatVersion)
        assertArrayEquals(bytes, envelope.payload)
        assertArrayEquals(bytes, QueensDefinition.encode(envelope))
    }

    /** Session codec: puzzle rides by value with its ref; roundtrip is lossless. */
    @Test
    fun `session codec round trips the fixture session`() {
        val daily = parsed()
        val session = QueensSession(ref = daily.ref, puzzle = daily.puzzle)
        val bytes = QueensSessionCodec.serialize(session)
        assertEquals(session, QueensSessionCodec.deserialize(bytes).getOrThrow())

        val bare = QueensSession(ref = null, puzzle = daily.puzzle)
        assertEquals(bare, QueensSessionCodec.deserialize(QueensSessionCodec.serialize(bare)).getOrThrow())
    }

    /** Score: the skeleton session is not record-eligible yet (completion stamp = queens-02+). */
    @Test
    fun `score model returns null while the session carries no completion stamp`() {
        assertNull(QueensScoreModel().completion(QueensSession(ref = parsed().ref, puzzle = parsed().puzzle)))
    }

    private fun <T> DecodeResult<T>.getOrThrow(): T = when (this) {
        is DecodeResult.Ok -> value
        is DecodeResult.Err -> throw AssertionError("decode failed: $error")
    }
}
