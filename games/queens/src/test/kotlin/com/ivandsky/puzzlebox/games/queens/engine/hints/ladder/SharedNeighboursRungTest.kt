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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D4: a cell touching every open cell of a unit still missing queens would smother it. */
class SharedNeighboursRungTest {

    /**
     * Region 0 is the diagonal pair 0 and 6, so cells 1 and 5 touch both. Region 3 is 12, 13,
     * 17, 22, and cell 18 touches all four.
     */
    private val five = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 1, 1, 1, 1,
            2, 0, 1, 1, 1,
            2, 2, 3, 3, 1,
            2, 2, 3, 4, 4,
            2, 2, 3, 4, 4,
        ),
    )

    /** 6×6, two queens per unit, each column its own region. */
    private val sixN2 = QueensTestSupport.puzzle(6, queensPerUnit = 2) { _, col -> col }

    private fun region(index: Int) = HintReason(HintReason.Kind.REGION, index, SharedNeighboursRung)
    private fun row(index: Int) = HintReason(HintReason.Kind.ROW, index, SharedNeighboursRung)

    private fun naming(puzzle: QueensPuzzle, state: QueensCandidates, unit: HintReason): QueensProof? =
        SharedNeighboursRung.steps(puzzle, state).singleOrNull { it.deduction.unit == unit }

    private fun empty(size: Int) = QueensCandidates(size, emptySet(), emptySet())

    @Test
    fun `the rung is the medium shared-neighbours rung`() {
        assertEquals("shared-neighbours", SharedNeighboursRung.id)
        assertEquals(Difficulty.Level.Medium, SharedNeighboursRung.level)
        assertEquals("queens.hint.rung.shared-neighbours", SharedNeighboursRung.captionKey)
    }

    @Test
    fun `the cells touching both cells of a diagonal region are crossed, naming the region`() {
        val proof = naming(five, empty(5), region(0))!!
        assertEquals(QueensDeduction.Exclude(region(0), setOf(1, 5)), proof.deduction)
        assertEquals(setOf(0, 6), proof.evidence)
    }

    @Test
    fun `a cell touching all four open cells of a region is crossed, one touching three is not`() {
        val proof = naming(five, empty(5), region(3))!!
        assertEquals(setOf(18), (proof.deduction as QueensDeduction.Exclude).cells)
        assertEquals(setOf(12, 13, 17, 22), proof.evidence)
    }

    @Test
    fun `evidence is the unit's open cells only, never a crossed cell`() {
        val state = QueensCandidates(5, emptySet(), setOf(17, 22))
        val proof = naming(five, state, region(3))!!
        assertEquals(setOf(12, 13), proof.evidence)
        assertEquals(setOf(7, 8, 18), (proof.deduction as QueensDeduction.Exclude).cells)
    }

    @Test
    fun `a unit down to one open cell is left to singles`() {
        assertNull(naming(five, QueensCandidates(5, emptySet(), setOf(6)), region(0)))
    }

    @Test
    fun `a unit already holding its queen yields nothing even with open cells left`() {
        assertNull(naming(five, QueensCandidates(5, setOf(12), emptySet()), region(3)))
    }

    @Test
    fun `on an N=2 board a unit missing two queens is judged on all its open cells`() {
        val state = QueensCandidates(6, emptySet(), setOf(9, 10, 11))
        val proof = naming(sixN2, state, row(1))!!
        assertEquals(setOf(1, 13), (proof.deduction as QueensDeduction.Exclude).cells)
        assertEquals(setOf(6, 7, 8), proof.evidence)
    }

    @Test
    fun `on an N=2 board a unit holding one queen is still missing one`() {
        val state = QueensCandidates(6, setOf(6), setOf(9, 10, 11))
        val proof = naming(sixN2, state, row(1))!!
        assertEquals(setOf(1, 2, 13, 14), (proof.deduction as QueensDeduction.Exclude).cells)
        assertEquals(setOf(7, 8), proof.evidence)
    }

    @Test
    fun `units come rows, then columns, then regions, lowest index first, and step is the first`() {
        val proofs = SharedNeighboursRung.steps(five, empty(5)).toList()
        val units = proofs.map { it.deduction.unit }
        assertEquals(units.sortedWith(compareBy({ it.kind }, { it.unit })), units)
        assertEquals(units.distinct(), units)
        assertEquals(proofs.first().deduction, SharedNeighboursRung.step(five, empty(5)))
    }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> {
        val text = SharedNeighboursRungTest::class.java.getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString()
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
            for (state in states(puzzle)) for (proof in SharedNeighboursRung.steps(puzzle, state)) {
                fired++
                val d = proof.deduction as QueensDeduction.Exclude
                assertTrue("$name $d", d.cells.none { it in solution })
                assertTrue("$name $d", d.cells.all(state::isOpen))
                assertEquals("$name $d", unitCells(puzzle, d.unit).filter(state::isOpen).toSet(), proof.evidence)
                assertTrue("$name $d", d.cells.intersect(proof.evidence).isEmpty())
            }
        }
        assertTrue(fired > 0)
    }
}
