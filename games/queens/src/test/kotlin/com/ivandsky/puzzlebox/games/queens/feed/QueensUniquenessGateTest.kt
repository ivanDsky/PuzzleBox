package com.ivandsky.puzzlebox.games.queens.feed

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The A13+A5 hard gate: the shipped solution must equal an independent exhaustive solve;
 * tampered solutions and ambiguous boards are rejected with typed errors — never playable,
 * no silent fallback. Fixtures are synthesized from the documented wire shape (research 02):
 * queens-daily-unique.json is provably 1-solution, queens-daily-ambiguous.json has exactly 2.
 */
class QueensUniquenessGateTest {

    private val date = LocalDate.of(2026, 11, 2)

    private fun fixture(name: String): String =
        QueensUniquenessGateTest::class.java.getResourceAsStream("/fixtures/$name")!!
            .readBytes().decodeToString()

    private fun parsed(name: String): QueensPuzzle =
        when (val r = QueensWireParser.parse(fixture(name), date)) {
            is DecodeResult.Ok -> r.value.puzzle
            is DecodeResult.Err -> throw AssertionError("fixture parse failed: ${r.error}")
        }

    /** DoD: the documented wire shape passes the gate — the daily is provably unique. */
    @Test
    fun `official-shape fixture passes the gate`() {
        assertNull(QueensUniquenessGate.check(parsed("queens-daily-unique.json")))
    }

    /** A-2 regression: wire `solution` array order is NOT guaranteed ascending-by-cell —
     *  the gate compares as sets, so a valid non-ascending daily is never falsely
     *  rejected as Untrusted. */
    @Test
    fun `non-ascending shipped solution order still passes the gate`() {
        val puzzle = parsed("queens-daily-unique.json")
        val shipped = puzzle.solution!!
        val reordered = shipped.asReversed()
        assertTrue("fixture sanity: order actually changed", reordered != shipped)
        val scrambled = QueensPuzzle(puzzle.size, puzzle.regions, puzzle.queensPerUnit, reordered)
        assertNull(QueensUniquenessGate.check(scrambled))
    }

    /** DoD: a tampered shipped solution is rejected with a typed decode error. */
    @Test
    fun `tampered solution is rejected`() {
        val puzzle = parsed("queens-daily-unique.json")
        val shipped = puzzle.solution!!
        // Move one queen to a cell that breaks the rules (same row as another queen).
        val tampered = shipped.toMutableList()
        tampered[1] = Cell(tampered[0].idx + 1)
        val broken = QueensPuzzle(puzzle.size, puzzle.regions, puzzle.queensPerUnit, tampered)
        val error = QueensUniquenessGate.check(broken)
        assertNotNull(error)
        assertTrue("expected typed Malformed, got $error", error is DecodeError.Malformed)
    }

    /** DoD: an ambiguous board (exactly 2 independent solutions) is rejected. */
    @Test
    fun `ambiguous board is rejected`() {
        val error = QueensUniquenessGate.check(parsed("queens-daily-ambiguous.json"))
        assertNotNull(error)
        val detail = (error as DecodeError.Malformed).detail
        assertTrue(
            "expected ambiguity detail, got $detail",
            detail.contains("ambiguous"),
        )
    }

    @Test
    fun `missing solution is rejected`() {
        val puzzle = parsed("queens-daily-unique.json")
        val bare = QueensPuzzle(puzzle.size, puzzle.regions, puzzle.queensPerUnit)
        val error = QueensUniquenessGate.check(bare)
        assertTrue(error is DecodeError.Malformed)
        assertEquals("daily ships no solution; A13 gate cannot validate", (error as DecodeError.Malformed).detail)
    }
}
