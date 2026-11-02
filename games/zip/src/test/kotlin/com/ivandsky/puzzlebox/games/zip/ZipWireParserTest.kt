package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import java.time.LocalDate
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wire parser: structural extraction of the captured payload shape (F2 §1), unknown fields ignored. */
class ZipWireParserTest {

    private val date = LocalDate.of(2026, 10, 30)

    @Test
    fun `parses gridSize, orderedSequence, walls, and solution`() {
        val json = """
            {
              "gridSize": 5,
              "orderedSequence": [0, 12, 24],
              "solution": [${ZipTestSupport.n5Solution.joinToString(",") { it.idx.toString() }}],
              "walls": [
                {"cellIdx": 0, "direction": "WallDirection_DOWN"},
                {"cellIdx": 2, "direction": "WallDirection_RIGHT"}
              ],
              "wallHints": [],
              "orderedSequencePositions": [
                {"cellIndex": 0, "isMystery": false},
                {"cellIndex": 12, "isMystery": false},
                {"cellIndex": 24, "isMystery": false}
              ]
            }
        """.trimIndent()

        val result = com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(json, date, "587")
        val daily = (result as DecodeResult.Ok).value
        assertEquals(ZipTestSupport.variantOf(5).id, daily.puzzle.variant)
        assertEquals(ZipTestSupport.path(0, 12, 24), daily.puzzle.board.waypoints)
        assertEquals(
            setOf(Wall(Cell(0), WallDirection.DOWN), Wall(Cell(2), WallDirection.RIGHT)),
            daily.puzzle.board.walls,
        )
        assertEquals(ZipTestSupport.n5Solution, daily.puzzle.solution)
        assertEquals(date, daily.ref.date)
        assertEquals(587, daily.ref.puzzleNumber)
    }

    /** The full wire spelling carries ${'$'}type discriminators; unknown keys are ignored (F2-5). */
    @Test
    fun `wire type discriminators are ignored`() {
        val json = """
            {
              "${'$'}type": "proto.com.linkedin.games.api.library.TrailGamePuzzle",
              "gridSize": 5,
              "orderedSequence": [0, 24]
            }
        """.trimIndent()
        val daily =
            (com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(json, date) as DecodeResult.Ok).value
        assertEquals(ZipTestSupport.path(0, 24), daily.puzzle.board.waypoints)
    }

    @Test
    fun `edition text is optional and non-numeric text yields a null number`() {
        val json = """{"gridSize": 5, "orderedSequence": [0, 12, 24]}"""
        val base = com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(json, date)
        assertNull((base as DecodeResult.Ok).value.ref.puzzleNumber)
        val custom = com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(json, date, "practice")
        assertNull((custom as DecodeResult.Ok).value.ref.puzzleNumber)
        assertEquals(date, (custom as DecodeResult.Ok).value.ref.date)
    }

    @Test
    fun `missing solution parses to a null-solution puzzle`() {
        val json = """{"gridSize": 5, "orderedSequence": [0, 12, 24]}"""
        val daily = (com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(json, date) as DecodeResult.Ok).value
        assertNull(daily.puzzle.solution)
        assertTrue(daily.puzzle.unknownTail.isEmpty()) // parser output is codec-clean
    }

    /** A PRESENT but unparseable solution is Malformed — never silently treated as absent. */
    @Test
    fun `present but unparseable solution is Malformed`() {
        val notAnArray = """{"gridSize": 5, "orderedSequence": [0, 12, 24], "solution": "0,1,2"}"""
        val err1 = (
            com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(notAnArray, date) as DecodeResult.Err
            ).error
        assertTrue("unexpected: $err1", err1 is DecodeError.Malformed)
        assertEquals("solution present but unparseable", (err1 as DecodeError.Malformed).detail)

        val nonInteger = """{"gridSize": 5, "orderedSequence": [0, 12, 24], "solution": [0, 1, "x"]}"""
        val err2 = (
            com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(nonInteger, date) as DecodeResult.Err
            ).error
        assertEquals(
            DecodeError.Malformed("solution present but unparseable"),
            err2,
        )
    }

    @Test
    fun `unknown variant size is UnknownVariant`() {
        val json = """{"gridSize": 4, "orderedSequence": [0, 15]}"""
        val error = (com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(json, date) as DecodeResult.Err).error
        assertTrue(error is DecodeError.UnknownVariant)
    }

    @Test
    fun `direction suffix parsing accepts the full wire spelling`() {
        val json = """
            {"gridSize": 5, "orderedSequence": [0, 24],
             "walls": [{"cellIdx": 1, "direction": "proto.com.linkedin.games.api.library.WallDirection_DOWN"}]}
        """.trimIndent()
        val daily =
            (com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(json, date) as DecodeResult.Ok).value
        assertEquals(setOf(Wall(Cell(1), WallDirection.DOWN)), daily.puzzle.board.walls)
    }

    /** D-6: structurally-bad input is a typed error from both overloads — callers branch,
     *  never catch. A non-primitive waypoint, or a wall entry that is not an object. */
    @Test
    fun `structurally bad payloads are Malformed, never thrown`() {
        listOf(
            """{"gridSize": 5, "orderedSequence": [0, {"x": 1}, 24]}""",
            """{"gridSize": 5, "orderedSequence": [0, 12, 24], "walls": ["oops"]}""",
            """{"gridSize": 5, "orderedSequence": [0, 12, 24], "walls": [7, true]}""",
            "not json at all",
        ).forEachIndexed { i, json ->
            val result = com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(json, date)
            assertTrue("case $i: $result", result is DecodeResult.Err)
            assertTrue((result as DecodeResult.Err).error is DecodeError.Malformed)
        }
        // The JsonObject overload is called directly by the fetch seam with pre-parsed JSON.
        val direct = com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser.parse(
            kotlinx.serialization.json.Json
                .parseToJsonElement("""{"gridSize": 5, "orderedSequence": [0, 12, 24], "walls": [true]}""")
                .jsonObject,
            date,
        )
        assertTrue(direct is DecodeResult.Err)
        assertTrue((direct as DecodeResult.Err).error is DecodeError.Malformed)
    }
}
