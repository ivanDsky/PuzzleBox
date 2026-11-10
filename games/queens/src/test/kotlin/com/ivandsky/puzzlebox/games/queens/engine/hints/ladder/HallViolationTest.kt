package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason.Kind
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HallViolation.Violation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The smallest set of units of one kind left too little room in another kind. */
class HallViolationTest {

    /** 5×5, each column its own region. */
    private val five = QueensTestSupport.puzzle(5) { _, col -> col }

    /** 6×6, two queens per unit, each column its own region. */
    private val sixN2 = QueensTestSupport.puzzle(6, queensPerUnit = 2) { _, col -> col }

    /** Columns 0 and 1 open on row 2 only. */
    private val pinnedToRowTwo = setOf(0, 1, 5, 6, 15, 16, 20, 21)

    @Test
    fun `an emptied unit is a violation of one unit with no target`() {
        val state = QueensCandidates(5, emptySet(), setOf(10, 11, 12, 13, 14))
        assertEquals(Violation(Kind.ROW, listOf(2), Kind.REGION, emptyList()), HallViolation.smallest(five, state))
    }

    @Test
    fun `two regions open on a single row are a violation of two`() {
        val violation = HallViolation.smallest(five, QueensCandidates(5, emptySet(), pinnedToRowTwo))!!
        assertEquals(Violation(Kind.REGION, listOf(0, 1), Kind.ROW, listOf(2)), violation)
        assertEquals(2, violation.k)
        assertEquals(listOf(0, 1, 5, 6, 10, 11, 15, 16, 20, 21), violation.cells(five))
    }

    @Test
    fun `the smallest violation wins over one found first`() {
        val state = QueensCandidates(5, emptySet(), pinnedToRowTwo + setOf(22, 23, 24))
        assertEquals(Violation(Kind.ROW, listOf(4), Kind.REGION, emptyList()), HallViolation.smallest(five, state))
    }

    @Test
    fun `a board with every cell open, and a solved board, have no violation`() {
        assertNull(HallViolation.smallest(five, QueensCandidates(5, emptySet(), emptySet())))
        val n4 = QueensTestSupport.n4Puzzle()
        assertNull(HallViolation.smallest(n4, QueensCandidates.start(n4, n4.solution!!.map { it.idx }.toSet())))
    }

    @Test
    fun `on an N=2 board the targets' room is their missing queens`() {
        val pinned = setOf(0, 6, 24, 30, 1, 7, 25, 31)
        assertNull(HallViolation.smallest(sixN2, QueensCandidates(6, emptySet(), pinned)))
        assertEquals(
            Violation(Kind.REGION, listOf(0, 1), Kind.ROW, listOf(2, 3)),
            HallViolation.smallest(sixN2, QueensCandidates(6, setOf(16), pinned)),
        )
    }

    @Test
    fun `on an N=2 board a unit with fewer open cells than missing queens is a violation of one`() {
        val state = QueensCandidates(6, emptySet(), setOf(0, 1, 2, 3, 4))
        assertEquals(Violation(Kind.ROW, listOf(0), Kind.REGION, listOf(5)), HallViolation.smallest(sixN2, state))
    }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> {
        val text = HallViolationTest::class.java.getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString()
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

    @Test
    fun `no state on the way to a golden board's solution has a violation`() {
        for ((name, puzzle) in goldenBoards()) {
            var state = QueensCandidates.start(puzzle, emptySet())
            while (true) {
                assertNull(name, HallViolation.smallest(puzzle, state))
                state = when (val d = QueensLadder.Default.next(puzzle, state) ?: break) {
                    is QueensDeduction.Place -> state.place(puzzle, d.cell)
                    is QueensDeduction.Exclude -> state.exclude(d.cells)
                }
            }
            val solution = puzzle.solution!!.map { it.idx }.sorted()
            for (placed in 1..solution.size) {
                assertNull(name, HallViolation.smallest(puzzle, QueensCandidates.start(puzzle, solution.take(placed).toSet())))
            }
        }
    }
}
