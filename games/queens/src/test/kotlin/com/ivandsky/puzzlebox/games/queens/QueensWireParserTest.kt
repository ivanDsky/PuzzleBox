package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wire parser: official daily shape (research 02), structural, never throws. */
class QueensWireParserTest {

    private val date = LocalDate.of(2026, 11, 2)

    private fun wire(
        n: Int,
        regions: List<List<Int>>,
        solution: List<Pair<Int, Int>>,
    ): String {
        val colorGrid = regions.joinToString(",") { row ->
            row.joinToString(",", prefix = """{"colors":[""", postfix = "]}")
        }
        val solutionJson = solution.joinToString(",") { """{"row":${it.first},"col":${it.second}}""" }
        return """{"gridSize":$n,"colorGrid":[$colorGrid],"solution":[$solutionJson]}"""
    }

    private val n8 = wire(
        n = 8,
        regions = List(8) { r -> List(8) { c -> (r + c) % 8 } },
        solution = listOf(0 to 0, 1 to 2, 2 to 4, 3 to 6, 4 to 1, 5 to 3, 6 to 5, 7 to 7),
    )

    private fun parse(json: String, edition: String? = null) = QueensWireParser.parse(json, date, edition)

    private fun ok(json: String, edition: String? = null): QueensWireParser.QueensDaily =
        when (val r = parse(json, edition)) {
            is DecodeResult.Ok -> r.value
            is DecodeResult.Err -> throw AssertionError("parse failed: ${r.error}")
        }

    @Test
    fun `parses gridSize, colorGrid, and solution into a QueensPuzzle`() {
        val daily = ok(n8)
        assertEquals(8, daily.puzzle.size)
        assertEquals(List(8) { r -> List(8) { c -> (r + c) % 8 } }.flatten(), daily.puzzle.regions)
        assertEquals(1, daily.puzzle.queensPerUnit)
        assertEquals(listOf(Cell(0), Cell(10), Cell(20), Cell(30), Cell(33), Cell(43), Cell(53), Cell(63)), daily.puzzle.solution)
        assertEquals(date, daily.ref.date)
        assertNull(daily.ref.puzzleNumber)
    }

    @Test
    fun `edition text lands on the ref`() {
        val regions = List(8) { r -> List(8) { c -> (r + c) % 8 } }
        val daily = ok(wire(8, regions, (0 until 8).map { it to (it * 3 + 1) % 8 }), "887")
        assertEquals(887, daily.ref.puzzleNumber)
    }

    @Test
    fun `unknown JSON fields are ignored`() {
        val withExtras = n8.dropLast(1) + ""","unknownFutureField":1}"""
        assertEquals(ok(n8), ok(withExtras))
    }

    @Test
    fun `parsing is deterministic`() {
        assertEquals(ok(n8), ok(n8))
    }

    @Test
    fun `malformed wire shapes are rejected`() {
        fun err(json: String): DecodeError = when (val r = parse(json)) {
            is DecodeResult.Err -> r.error
            is DecodeResult.Ok -> throw AssertionError("expected rejection: $json")
        }
        assertTrue(err("not json") is DecodeError.Malformed)
        assertTrue(err("""{"colorGrid":[]}""") is DecodeError.Malformed) // no gridSize
        assertTrue(err("""{"gridSize": 8}""") is DecodeError.Malformed) // no colorGrid
        val colors8 = List(8) { List(8) { 0 } }.joinToString(",") { row ->
            row.joinToString(",", prefix = """{"colors":[""", postfix = "]}")
        }
        assertTrue(err("""{"gridSize": 8, "colorGrid": [$colors8], "solution": [}""") is DecodeError.Malformed) // broken solution
        // short colorGrid row on a known size
        assertTrue(
            err("""{"gridSize": 8, "colorGrid": [{"colors":[0,1]}]}""") is DecodeError.Malformed,
        )
        // raw array rows (shape drift) are malformed, not guessed
        assertTrue(
            err("""{"gridSize": 8, "colorGrid": [[0,1],[1,3]]}""") is DecodeError.Malformed,
        )
        // region id out of range on a known size
        assertTrue(
            err("""{"gridSize": 8, "colorGrid": [{"colors":[8,0,0,0,0,0,0,0]},{"colors":[0,0,0,0,0,0,0,0]},{"colors":[0,0,0,0,0,0,0,0]},{"colors":[0,0,0,0,0,0,0,0]},{"colors":[0,0,0,0,0,0,0,0]},{"colors":[0,0,0,0,0,0,0,0]},{"colors":[0,0,0,0,0,0,0,0]},{"colors":[0,0,0,0,0,0,0,0]}], "solution": [}""") is DecodeError.Malformed,
        )
        // gridSize without a variant-table entry -> UnknownVariant (never guessed, ADR-0005)
        assertTrue(
            err("""{"gridSize": 6, "colorGrid": [[0],[0],[0],[0],[0],[0]], "solution": []}""") is DecodeError.UnknownVariant,
        )
        // wrong queen count for the variant
        assertTrue(
            err("""{"gridSize": 8, "colorGrid": [$colors8], "solution": [{"row":0,"col":0}]}""") is DecodeError.Malformed,
        )
        // duplicate queen cell
        assertTrue(
            err("""{"gridSize": 8, "colorGrid": [$colors8], "solution": [{"row":0,"col":0},{"row":0,"col":0},{"row":1,"col":1},{"row":2,"col":2},{"row":3,"col":3},{"row":4,"col":4},{"row":5,"col":5},{"row":6,"col":6},{"row":7,"col":7}]}""") is DecodeError.Malformed,
        )
        // solution cell out of range
        assertTrue(
            err("""{"gridSize": 8, "colorGrid": [$colors8], "solution": [{"row":0,"col":0},{"row":9,"col":9},{"row":2,"col":2},{"row":3,"col":3},{"row":4,"col":4},{"row":5,"col":5},{"row":6,"col":6},{"row":7,"col":7}]}""") is DecodeError.Malformed,
        )
    }
}
