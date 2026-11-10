package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D8: a queen ruling out every open cell of a unit still missing queens would leave it no room. */
class SqueezeRungTest {

    /**
     * Region 1 is 3, 4, 8, 9 and 10, so with 1, 2 and 4 crossed, row 0 is open on 0 and 3
     * only: cell 10 reaches 0 down column 0 and 3 through region 1, without touching either.
     */
    private val five = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 0, 1, 1,
            2, 2, 2, 1, 1,
            1, 2, 2, 3, 3,
            4, 4, 3, 3, 3,
            4, 4, 4, 3, 3,
        ),
    )
    private val rowZeroOpenOnZeroAndThree = QueensCandidates(5, emptySet(), setOf(1, 2, 4))

    /** 6×6, two queens per unit, each column its own region. */
    private val sixN2 = QueensTestSupport.puzzle(6, queensPerUnit = 2) { _, col -> col }

    private fun row(index: Int) = HintReason(HintReason.Kind.ROW, index, SqueezeRung)
    private fun region(index: Int) = HintReason(HintReason.Kind.REGION, index, SqueezeRung)

    private fun naming(puzzle: QueensPuzzle, state: QueensCandidates, unit: HintReason): QueensProof? =
        SqueezeRung.steps(puzzle, state).singleOrNull { it.deduction.unit == unit }

    private fun excluded(proof: QueensProof): Set<Int> = (proof.deduction as QueensDeduction.Exclude).cells

    @Test
    fun `the rung is the hard squeeze rung`() {
        assertEquals("squeeze", SqueezeRung.id)
        assertEquals(Difficulty.Level.Hard, SqueezeRung.level)
        assertEquals("queens.hint.rung.squeeze", SqueezeRung.captionKey)
    }

    @Test
    fun `a cell reaching both open cells of a row by column and region is crossed, naming the row`() {
        val proof = naming(five, rowZeroOpenOnZeroAndThree, row(0))!!
        assertEquals(QueensDeduction.Exclude(row(0), setOf(10)), proof.deduction)
        assertEquals(setOf(0, 3), proof.evidence)
    }

    @Test
    fun `a cell reaching only one of the open cells is not crossed`() {
        val crossed = excluded(naming(five, rowZeroOpenOnZeroAndThree, row(0))!!)
        assertFalse(5 in crossed)
        assertFalse(8 in crossed)
    }

    @Test
    fun `a unit down to one open cell is left to singles`() {
        assertNull(naming(five, rowZeroOpenOnZeroAndThree, region(0)))
    }

    @Test
    fun `a unit already holding its queen yields nothing even with open cells left`() {
        assertNull(naming(five, QueensCandidates(5, setOf(1), setOf(2, 4)), row(0)))
    }

    @Test
    fun `on an N=2 board a shared unit rules out only once the queen fills it, and a cell of the unit is never listed`() {
        // Row 1 holds 6 and is open on 8 and 10: a queen on 8 would fill the row, but 8 is the row's own.
        val proof = naming(sixN2, QueensCandidates(6, setOf(6), setOf(7, 9, 11)), row(1))!!
        assertEquals(setOf(3, 15), excluded(proof))
        assertEquals(setOf(8, 10), proof.evidence)
    }

    @Test
    fun `units come rows, then columns, then regions, lowest index first, and step is the first`() {
        val proofs = SqueezeRung.steps(five, rowZeroOpenOnZeroAndThree).toList()
        val units = proofs.map { it.deduction.unit }
        assertEquals(listOf(row(0), HintReason(HintReason.Kind.COLUMN, 4, SqueezeRung), region(1)), units)
        assertEquals(proofs.first().deduction, SqueezeRung.step(five, rowZeroOpenOnZeroAndThree))
    }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> {
        val text = SqueezeRungTest::class.java.getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString()
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

    /** The Default-ladder solve from empty, then the known solution stood queen by queen with nothing crossed. */
    private fun states(puzzle: QueensPuzzle): List<QueensCandidates> {
        var state = QueensCandidates.start(puzzle, emptySet())
        val seen = mutableListOf(state)
        while (true) {
            state = when (val d = QueensLadder.Default.next(puzzle, state) ?: break) {
                is QueensDeduction.Place -> state.place(puzzle, d.cell)
                is QueensDeduction.Exclude -> state.exclude(d.cells)
            }
            seen += state
        }
        val solution = puzzle.solution!!.map { it.idx }.sorted()
        return seen + (1..solution.size).map { QueensCandidates(puzzle.size, solution.take(it).toSet(), emptySet()) }
    }

    private fun unitCells(puzzle: QueensPuzzle, unit: HintReason): Set<Int> {
        val n = puzzle.size
        return when (unit.kind) {
            HintReason.Kind.ROW -> (0 until n).map { unit.unit * n + it }
            HintReason.Kind.COLUMN -> (0 until n).map { it * n + unit.unit }
            HintReason.Kind.REGION -> puzzle.regions.indices.filter { puzzle.regions[it] == unit.unit }
        }.toSet()
    }

    @Test
    fun `no cell the rung crosses on a golden board is a solution cell, and evidence is the named unit's open cells`() {
        var fired = 0
        for ((name, puzzle) in goldenBoards()) {
            val solution = puzzle.solution!!.map { it.idx }.toSet()
            for (state in states(puzzle)) for (proof in SqueezeRung.steps(puzzle, state)) {
                fired++
                val d = proof.deduction as QueensDeduction.Exclude
                val unit = unitCells(puzzle, d.unit)
                assertTrue("$name $d", d.cells.none { it in solution })
                assertTrue("$name $d", d.cells.all(state::isOpen))
                assertTrue("$name $d", d.cells.none { it in unit })
                assertEquals("$name $d", unit.filter(state::isOpen).toSet(), proof.evidence)
            }
        }
        assertTrue(fired > 0)
    }
}
