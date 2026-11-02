package com.ivandsky.puzzlebox.games.queens.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture test: the documented GraphQL wire shape (research 02) extracts into the exact
 * queensGamePuzzle JSON the wire parser consumes — rotation-tolerant, mention-immune.
 */
class QueensGameExtractorTest {

    private val fixture: String =
        QueensGameExtractorTest::class.java.getResourceAsStream("/fixtures/queens-daily-graphql.json")!!
            .readBytes().decodeToString()

    @Test
    fun `extracts data-bearing object from graphql included array`() {
        val match = QueensGameExtractor.extract(fixture)!!
        assertTrue(match.queensGamePuzzleJson.contains("\"gridSize\""))
        assertTrue(match.queensGamePuzzleJson.contains("\"colorGrid\""))
        assertTrue(match.queensGamePuzzleJson.contains("\"solution\""))
    }

    @Test
    fun `extracted object is the puzzle not the wrapper`() {
        val match = QueensGameExtractor.extract(fixture)!!
        // The brace-balanced region must parse as the puzzle object: starts at gridSize.
        assertTrue(match.queensGamePuzzleJson.startsWith("{\"gridSize\""))
    }

    @Test
    fun `mention in code text never wins`() {
        val body = """{"included":[],"shell":"var q = queensGamePuzzle; render(q.gridSize);"}"""
        assertNull(QueensGameExtractor.extract(body))
    }

    @Test
    fun `object without numeric gridSize is not data`() {
        val body = """{"x":1,"queensGamePuzzle":{"gridSize":"eight"}}"""
        assertNull(QueensGameExtractor.extract(body))
    }

    @Test
    fun `absent puzzle yields null`() {
        assertNull(QueensGameExtractor.extract("{}"))
    }

    @Test
    fun `first data occurrence wins deterministically`() {
        val puzzle = """{"gridSize":8}"""
        val body = """{"a":{"queensGamePuzzle":$puzzle},"b":{"queensGamePuzzle":{"gridSize":9}}}"""
        assertEquals(puzzle, QueensGameExtractor.extract(body)!!.queensGamePuzzleJson)
    }
}
