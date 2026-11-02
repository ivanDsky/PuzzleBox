package com.ivandsky.puzzlebox.games.queens.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REAL captured LinkedIn Queens daily (queens-09 HITL device session, edition 916,
 * 2026-11-02): the board is 7×7 — research 01's "dailies ship 8×8–10×10" was wrong,
 * dailies rotate sizes. Fixture is the trimmed stream body: the queensGamePuzzle object
 * (with its real `$type` wrappers, which the structural parser ignores) plus the
 * edition text, wrapped so [QueensGameExtractor] scans it exactly like a live fetch.
 *
 * Closes ledger finding A-3 (no real fixture): the full production path
 * extractor -> wire parser -> A13 uniqueness gate must accept today's daily, and the
 * gate's independent solve must equal the shipped solution.
 */
class QueensRealDailyFixtureTest {

    private val body: String =
        QueensRealDailyFixtureTest::class.java.getResourceAsStream("/fixtures/queens-daily-real-916.json")!!
            .readBytes().decodeToString()

    @Test
    fun `real 7x7 daily passes extractor, wire parser and uniqueness gate`() {
        val match = QueensGameExtractor.extract(body)
        assertNotNull("extractor found nothing in real body", match)
        assertEquals("916", match!!.editionText)
        assertTrue("not the real 7x7 board", match.queensGamePuzzleJson.contains("\"gridSize\":7"))

        val parsed = QueensWireParser.parse(match.queensGamePuzzleJson, LocalDate.of(2026, 11, 2), match.editionText)
        assertTrue("wire parse failed: ${parsed}", parsed is DecodeResult.Ok)
        val daily = (parsed as DecodeResult.Ok).value
        assertEquals(7, daily.puzzle.size)
        assertNotNull("real daily must ship a solution", daily.puzzle.solution)

        assertNull(
            "A13 gate rejected the real daily: ${QueensUniquenessGate.check(daily.puzzle)}",
            QueensUniquenessGate.check(daily.puzzle),
        )
    }
}
