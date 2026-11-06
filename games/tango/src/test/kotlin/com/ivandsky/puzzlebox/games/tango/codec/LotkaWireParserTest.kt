package com.ivandsky.puzzlebox.games.tango.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.feed.TangoGameExtractor
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real LinkedIn lotka payloads (research 02 samples) in both wire shapes (spec A7). */
class LotkaWireParserTest {

    private val day = LocalDate.of(2026, 11, 5)

    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { name }.readText()

    private fun extracted(name: String) = requireNotNull(TangoGameExtractor.extract(fixture(name))) { name }

    private fun ok(json: String, edition: String? = null): LotkaWireParser.TangoDaily {
        val parsed = LotkaWireParser.parse(json, day, edition)
        assertTrue("expected Ok, got $parsed", parsed is DecodeResult.Ok)
        return (parsed as DecodeResult.Ok).value
    }

    /** The wire's own solution array, ZERO=SUN / ONE=MOON, prefix-tolerant. */
    private fun wireSolution(lotkaJson: String): List<TangoCell> =
        (Json.parseToJsonElement(lotkaJson).jsonObject["solution"] as JsonArray).map {
            if (it.jsonPrimitive.content.endsWith("ZERO")) TangoCell.SUN else TangoCell.MOON
        }

    private fun err(json: String): DecodeError {
        val parsed = LotkaWireParser.parse(json, day)
        assertTrue("expected Err, got $parsed", parsed is DecodeResult.Err)
        return (parsed as DecodeResult.Err).error
    }

    @Test
    fun `live stream fixture parses to today's uniquely solvable n6 puzzle`() {
        val match = extracted("01-tango-stream-today.txt")
        val daily = ok(match.lotkaGamePuzzleJson, match.editionText)

        assertEquals("tango/n6", daily.puzzle.variant.value)
        assertEquals(759, daily.ref.puzzleNumber)
        assertEquals(day, daily.ref.date)
        // Cross-checked against community #759 (research 04): row 0 starts S S M.
        assertEquals(
            mapOf(
                0 to TangoCell.SUN, 1 to TangoCell.SUN, 2 to TangoCell.MOON, 7 to TangoCell.SUN,
                25 to TangoCell.MOON, 30 to TangoCell.SUN, 31 to TangoCell.MOON, 32 to TangoCell.MOON,
            ),
            daily.puzzle.givens,
        )
        assertEquals(
            listOf(
                TangoMarker(3, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(4, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(33, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(4, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(28, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
            daily.puzzle.markers,
        )
        assertEquals(wireSolution(match.lotkaGamePuzzleJson), TangoUniqueness.solve(daily.puzzle))
    }

    @Test
    fun `by-id payloads 1, 301 and 758 parse to uniquely solvable puzzles matching their wire solution`() {
        for (number in listOf(1, 301, 758)) {
            val match = extracted("linkedin-tango-byid-$number.json")
            val daily = ok(match.lotkaGamePuzzleJson)

            assertEquals("#$number", "tango/n6", daily.puzzle.variant.value)
            assertTrue("#$number givens", daily.puzzle.givens.isNotEmpty())
            assertEquals("#$number", wireSolution(match.lotkaGamePuzzleJson), TangoUniqueness.solve(daily.puzzle))
        }
    }

    @Test
    fun `either edge endpoint may come first`() {
        val daily = ok(lotka(edges = """[{"startIdx":9,"endIdx":3,"isEqual":true},{"startIdx":1,"endIdx":0,"isEqual":false}]"""))

        assertEquals(
            listOf(
                TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
            ),
            daily.puzzle.markers,
        )
    }

    @Test
    fun `absent presets and edges mean none`() {
        val daily = ok("""{"gridSize":6,"solution":$SOLUTION}""")

        assertTrue(daily.puzzle.givens.isEmpty())
        assertTrue(daily.puzzle.markers.isEmpty())
        assertNull(daily.ref.puzzleNumber)
    }

    @Test
    fun `structurally broken payloads are Malformed`() {
        val broken = listOf(
            "not json",
            """{"solution":$SOLUTION}""",
            lotka(gridSize = 99),
            """{"gridSize":6,"solution":["ZERO"]}""",
            """{"gridSize":6,"solution":${SOLUTION.replaceFirst("ZERO", "TWO")}}""",
            lotka(presets = "[36]"),
            lotka(edges = """[{"startIdx":5,"endIdx":6,"isEqual":true}]"""),
            lotka(edges = """[{"startIdx":0,"endIdx":1}]"""),
        )
        for (json in broken) {
            assertTrue(json, err(json) is DecodeError.Malformed)
        }
    }

    private fun lotka(gridSize: Int = 6, presets: String = "[0]", edges: String = "[]") =
        """{"gridSize":$gridSize,"presetCellIdxes":$presets,"edges":$edges,"solution":$SOLUTION}"""

    private companion object {
        /** A valid 6×6 Tango solution (the bundled seed's), by-id token shape. */
        const val SOLUTION = """["ZERO","ZERO","ONE","ZERO","ONE","ONE","ZERO","ONE","ZERO","ONE","ONE","ZERO",""" +
            """"ONE","ZERO","ONE","ZERO","ZERO","ONE","ZERO","ONE","ZERO","ONE","ONE","ZERO",""" +
            """"ONE","ONE","ZERO","ONE","ZERO","ZERO","ONE","ZERO","ONE","ZERO","ZERO","ONE"]"""
    }
}
