package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason.Kind
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HallViolation.Violation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The look-ahead chain and the D10/D11 rungs that cross a cell by the rounds its chain needs to break. */
class LookAheadChainTest {

    private val cheap = listOf(LineLockRung, SharedNeighboursRung, PigeonholeRung, LineGroupPigeonholeRung, SqueezeRung, LeftoverQueenRung)
    private val short = ShortLookAheadRung(cheap)
    private val long = LongLookAheadRung(cheap)

    /** 5×5, each column its own region, so only rows, columns and touching matter. */
    private val five = QueensTestSupport.puzzle(5) { _, col -> col }

    /** A queen on 0 leaves row 1 the single 7, and 7 takes both open cells of row 2 (11, 13). */
    private val oneRound = QueensCandidates(5, emptySet(), setOf(8, 9, 10, 12, 14))

    /**
     * A queen on 0 leaves no single, so shared neighbours crosses 13 (it touches all of row 1's
     * 7, 8, 9); that leaves row 2 the single 11, which takes both open cells of row 3 (16, 17).
     */
    private val cheapThenForced = QueensCandidates(5, emptySet(), setOf(12, 14, 15, 18, 19))

    /** A queen on 0 leaves rows 1 and 2 the singles 7 and 13, which touch. */
    private val collision = QueensCandidates(5, emptySet(), setOf(8, 9, 11, 12, 14))

    /**
     * A queen on 0 forces 7; then column 1 is down to 21 and row 2 to 14, both forced in round
     * two, and 14 with 21 leave column 3 (region 3) no open cell.
     */
    private val twoForcedRounds = QueensCandidates(5, emptySet(), setOf(8, 9, 12, 13, 15, 16, 17))

    private fun crossed(rung: QueensRung, puzzle: QueensPuzzle, state: QueensCandidates): List<Int> =
        rung.steps(puzzle, state).map { (it.deduction as QueensDeduction.Exclude).cells.single() }.toList()

    @Test
    fun `a queen whose one forced cell empties a unit breaks after one round`() {
        assertEquals(
            LookAheadChain.Break(0, 1, listOf(7), Violation(Kind.ROW, listOf(2), Kind.REGION, emptyList())),
            LookAheadChain.run(five, oneRound, 0, cheap),
        )
    }

    @Test
    fun `a cheap exclusion before the forced cell makes two rounds`() {
        val before = cheapThenForced.copy()
        assertNull(SinglesRung.step(five, cheapThenForced.place(five, 0)))
        assertEquals(
            LookAheadChain.Break(0, 2, listOf(11), Violation(Kind.ROW, listOf(3), Kind.REGION, emptyList())),
            LookAheadChain.run(five, cheapThenForced, 0, cheap),
        )
        assertEquals(before, cheapThenForced)
    }

    @Test
    fun `two forced queens that attack each other break at the round that forced them`() {
        val forced = SinglesRung.steps(five, collision.place(five, 0)).map { (it.deduction as QueensDeduction.Place).cell }.toList()
        assertEquals(listOf(7, 13), forced)
        assertTrue(QueensAttack.attacks(five, 7, 13))
        assertEquals(
            LookAheadChain.Break(0, 1, listOf(7), Violation(Kind.ROW, listOf(2), Kind.REGION, emptyList())),
            LookAheadChain.run(five, collision, 0, cheap),
        )
    }

    @Test
    fun `every queen forced on the way is kept in order`() {
        assertEquals(
            LookAheadChain.Break(0, 2, listOf(7, 21, 14), Violation(Kind.REGION, listOf(3), Kind.ROW, emptyList())),
            LookAheadChain.run(five, twoForcedRounds, 0, cheap),
        )
    }

    @Test
    fun `a chain that stalls without breaking proves nothing`() {
        // n4's last solution cell: a queen there fills the board and nothing breaks or applies.
        val puzzle = QueensTestSupport.n4Puzzle()
        val state = QueensCandidates.start(puzzle, setOf(1, 7, 8))
        assertNull(LookAheadChain.run(puzzle, state, 14, cheap))
        assertFalse(14 in crossed(short, puzzle, state) + crossed(long, puzzle, state))
    }

    @Test
    fun `a chain still unbroken at the cap proves nothing`() {
        assertEquals(24, LookAheadChain.CAP)
        val scratch = HallViolation.Scratch(five)
        assertNull(LookAheadChain.run(five, cheapThenForced, 0, cheap, scratch, cap = 1))
        assertEquals(2, LookAheadChain.run(five, cheapThenForced, 0, cheap, scratch, cap = 2)?.rounds)
    }

    @Test
    fun `the rungs are the expert short and long look-ahead`() {
        assertEquals("short-look-ahead", short.id)
        assertEquals("queens.hint.rung.short-look-ahead", short.captionKey)
        assertEquals("long-look-ahead", long.id)
        assertEquals("queens.hint.rung.long-look-ahead", long.captionKey)
        assertEquals(Difficulty.Level.Expert, short.level)
        assertEquals(Difficulty.Level.Expert, long.level)
    }

    @Test
    fun `a short proof names the broken unit and reads its open cells plus the forced queen`() {
        val proof = short.steps(five, oneRound).single { 0 in (it.deduction as QueensDeduction.Exclude).cells }
        assertEquals(QueensDeduction.Exclude(short, HintReason(Kind.ROW, 2, short), setOf(0)), proof.deduction)
        assertEquals(setOf(7, 11, 13), proof.evidence)
        assertFalse(0 in crossed(long, five, oneRound))
    }

    @Test
    fun `a long proof adds every queen the earlier rounds forced`() {
        val proof = long.steps(five, twoForcedRounds).single { 0 in (it.deduction as QueensDeduction.Exclude).cells }
        assertEquals(QueensDeduction.Exclude(long, HintReason(Kind.REGION, 3, long), setOf(0)), proof.deduction)
        // Region 3's cells still open on the board (3, 18, 23) and the queens 7, 21, 14.
        assertEquals(setOf(3, 7, 14, 18, 21, 23), proof.evidence)
        assertFalse(0 in crossed(short, five, twoForcedRounds))
    }

    @Test
    fun `a cheap round puts the cell on the long rung`() {
        val proof = long.steps(five, cheapThenForced).single { 0 in (it.deduction as QueensDeduction.Exclude).cells }
        assertEquals(HintReason(Kind.ROW, 3, long), proof.deduction.unit)
        assertEquals(setOf(11, 16, 17), proof.evidence)
    }

    @Test
    fun `steps run by fewest rounds then lowest cell and step is the first`() {
        for ((rung, state) in listOf(short to oneRound, long to cheapThenForced, long to twoForcedRounds)) {
            val keys = crossed(rung, five, state).map { LookAheadChain.run(five, state, it, cheap)!!.rounds to it }
            assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second })), keys)
            assertEquals(rung.steps(five, state).first().deduction, rung.step(five, state))
        }
    }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> {
        val text = javaClass.getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString()
        return Json.parseToJsonElement(text).jsonObject["boards"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            fun ints(key: String) = o[key]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
            o["name"]!!.jsonPrimitive.content to QueensPuzzle(
                size = o["size"]!!.jsonPrimitive.content.toInt(),
                regions = ints("regions"),
                queensPerUnit = o["queensPerUnit"]!!.jsonPrimitive.content.toInt(),
                solution = ints("solve").map(::Cell),
            )
        }
    }

    /** The known solution stood queen by queen with nothing crossed: the states with the most open cells to probe. */
    private fun prefixes(puzzle: QueensPuzzle): List<QueensCandidates> {
        val solution = puzzle.solution!!.map { it.idx }.sorted()
        return (0 until solution.size).map { QueensCandidates.start(puzzle, solution.take(it).toSet()) }
    }

    @Test
    fun `on an N=2 board a queen leaving a unit fewer open cells than missing queens is a short look-ahead`() {
        // Row 3 misses 2 queens on 18, 20, 22; a queen on 13 touches 18 and 20. Not every cell is crossed, so squeeze passes it, and k = 1 is not count squeeze's.
        val sixN2 = QueensTestSupport.puzzle(6, queensPerUnit = 2) { _, col -> col }
        val state = QueensCandidates(6, emptySet(), setOf(19, 21, 23))
        val proof = short.steps(sixN2, state).single { 13 in (it.deduction as QueensDeduction.Exclude).cells }
        assertEquals(QueensDeduction.Exclude(short, HintReason(Kind.ROW, 3, short), setOf(13)), proof.deduction)
        assertEquals(setOf(18, 20, 22), proof.evidence)
        assertEquals(0, LookAheadChain.run(sixN2, state, 13, cheap)?.rounds)
        val lower = (SqueezeRung.steps(sixN2, state) + CountSqueezeRung.steps(sixN2, state)).flatMap { (it.deduction as QueensDeduction.Exclude).cells }
        assertFalse(13 in lower.toSet())
    }

    @Test
    fun `short and long never cross the same cell, and neither crosses a cell whose chain proves nothing`() {
        var fired = 0
        for ((name, puzzle) in goldenBoards()) for (state in prefixes(puzzle)) {
            val shortCells = crossed(short, puzzle, state)
            val longCells = crossed(long, puzzle, state)
            fired += shortCells.size + longCells.size
            assertTrue("$name", shortCells.intersect(longCells.toSet()).isEmpty())
            val scratch = HallViolation.Scratch(puzzle)
            for (cell in puzzle.regions.indices.filter(state::isOpen)) {
                val chain = LookAheadChain.run(puzzle, state, cell, cheap, scratch)
                when {
                    chain == null -> assertFalse("$name $cell", cell in shortCells || cell in longCells)
                    chain.rounds <= 1 -> assertTrue("$name $cell", cell in shortCells)
                    else -> assertTrue("$name $cell", cell in longCells)
                }
            }
        }
        assertTrue(fired > 0)
    }
}
