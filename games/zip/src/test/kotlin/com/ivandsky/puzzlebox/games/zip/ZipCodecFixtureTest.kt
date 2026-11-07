package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C6 + I9 against the captured production payload (#592): the wire parser, the A16
 * oracle gate, and the codec agree on one real puzzle.
 */
class ZipCodecFixtureTest {

    private val fixtureJson: String by lazy {
        ZipCodecFixtureTest::class.java.getResourceAsStream("/fixtures/zip-daily-592.json")!!
            .readBytes().decodeToString()
    }
    private val date = LocalDate.of(2026, 10, 30)

    private fun parsed() = ZipWireParser.parse(fixtureJson, date, "592").let { result ->
        when (result) {
            is DecodeResult.Ok -> result.value
            is DecodeResult.Err -> throw AssertionError("fixture parse failed: ${result.error}")
        }
    }

    /** The A16 gate on the real payload: shipped solution verifies, solver agrees uniquely. */
    @Test
    fun `A16 gate - shipped solution verifies and the solver agrees it is unique`() {
        val daily = parsed()
        val shipped = daily.puzzle.solution!!
        assertTrue(ZipRules.verifyPath(daily.puzzle, shipped))
        val solver = ZipSolver()
        assertEquals(1, solver.countSolutions(daily.puzzle))
        assertEquals(shipped, solver.solve(daily.puzzle))
    }

    /** Identity comes with the fetch: day 2026-10-30, edition "592" -> puzzleNumber 592. */
    @Test
    fun `fixture identity lands on the ref`() {
        val daily = parsed()
        assertEquals(date, (daily.ref.key as PuzzleKey.Daily).date)
        assertEquals(592, (daily.ref.key as PuzzleKey.Daily).puzzleNumber)
        assertEquals(ZipTestSupport.variantOf(6).id, daily.puzzle.variant)
        assertEquals(4, daily.puzzle.board.walls.size)
        assertEquals(listOf(Cell(10), Cell(19), Cell(18), Cell(11), Cell(25), Cell(16)), daily.puzzle.board.waypoints)
    }

    /** C6 round trip on the production puzzle; canonical bytes re-encode identically. */
    @Test
    fun `C6 - production fixture round trips through the codec`() {
        val puzzle = parsed().puzzle
        val bytes = ZipCodec.encode(puzzle)
        assertEquals(puzzle, ZipCodec.decode(bytes).getOrThrow())
        assertArrayEquals(bytes, ZipCodec.encode(ZipCodec.decode(bytes).getOrThrow()))
    }

    /** Parsing is pure and repeatable: same JSON, same puzzle, no hidden state. */
    @Test
    fun `parsing is deterministic`() {
        assertEquals(parsed(), parsed())
        // structurally identical JSON with unknown extra fields parses the same
        val withExtras = Json.parseToJsonElement(fixtureJson).let { element ->
            val obj = element as kotlinx.serialization.json.JsonObject
            kotlinx.serialization.json.JsonObject(obj + mapOf("unknownFutureField" to kotlinx.serialization.json.JsonPrimitive(1)))
        }
        assertEquals(parsed(), ZipWireParser.parse(withExtras, date, "592").getOrThrow())
    }

    /** Malformed wire shapes are Malformed, never crashes or guessed boards. */
    @Test
    fun `malformed payloads are rejected`() {
        fun err(json: String): DecodeError = when (val r = ZipWireParser.parse(json, date)) {
            is DecodeResult.Err -> r.error
            is DecodeResult.Ok -> throw AssertionError("expected rejection: $json")
        }

        assertTrue(err("""{"gridSize": 6}""") is DecodeError.Malformed) // no orderedSequence
        assertTrue(err("""{"orderedSequence": [1]}""") is DecodeError.Malformed) // no gridSize
        assertTrue(err("""not json""") is DecodeError.Malformed)
        assertTrue(err("""{"gridSize": 4, "orderedSequence": [1, 2]}""") is DecodeError.UnknownVariant) // no n4 daily regime
        assertTrue(
            err("""{"gridSize": 6, "orderedSequence": [10, 10]}""") is DecodeError.Malformed, // duplicate waypoint
        )
        assertTrue(
            err("""{"gridSize": 6, "orderedSequence": [99]}""") is DecodeError.Malformed, // out of range
        )
        assertTrue(
            err(
                """{"gridSize": 6, "orderedSequence": [1], "walls": [{"cellIdx": 3, "direction": "WallDirection_LEFT"}]}""",
            ) is DecodeError.Malformed, // unknown direction
        )
        assertTrue(
            err(
                """{"gridSize": 6, "orderedSequence": [1], "solution": [1, 2, 3]}""",
            ) is DecodeError.Malformed, // not a permutation
        )
    }
}
