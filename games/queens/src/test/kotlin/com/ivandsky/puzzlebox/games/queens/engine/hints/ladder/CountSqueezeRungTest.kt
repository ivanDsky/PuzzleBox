package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D9: a queen that would leave k ≥ 2 units fewer than k units of another kind to stand in. */
class CountSqueezeRungTest {

    /** 5×5, each column its own region. */
    private val five = QueensTestSupport.puzzle(5) { _, col -> col }

    /**
     * Regions 0 and 1 are open on rows 1 and 4 only (5, 6, 20, 21). A queen on row 4 that
     * spares row 3 leaves both on row 1 alone; one on row 1 leaves both on row 4 alone.
     */
    private val twoRows = QueensCandidates(5, emptySet(), setOf(0, 1, 10, 11, 15, 16))

    private fun region(index: Int) = HintReason(HintReason.Kind.REGION, index, CountSqueezeRung)

    private fun excluded(proof: QueensProof): Set<Int> = (proof.deduction as QueensDeduction.Exclude).cells

    @Test
    fun `the rung is the hard count-squeeze rung`() {
        assertEquals("count-squeeze", CountSqueezeRung.id)
        assertEquals(Difficulty.Level.Hard, CountSqueezeRung.level)
        assertEquals("queens.hint.rung.count-squeeze", CountSqueezeRung.captionKey)
    }

    @Test
    fun `a cell whose queen leaves two regions one row is crossed, naming the lower region`() {
        val proof = CountSqueezeRung.steps(five, twoRows).single { 22 in excluded(it) }
        assertEquals(region(0), proof.deduction.unit)
        assertEquals(setOf(5, 6, 20, 21), proof.evidence)
    }

    @Test
    fun `cells causing the same violation share one exclusion, in order of their lowest cell, and step is the first`() {
        val proofs = CountSqueezeRung.steps(five, twoRows).toList()
        assertEquals(
            listOf(
                QueensDeduction.Exclude(CountSqueezeRung, region(0), setOf(7, 9)),
                QueensDeduction.Exclude(CountSqueezeRung, region(0), setOf(22, 24)),
            ),
            proofs.map { it.deduction },
        )
        assertEquals(proofs.first().deduction, CountSqueezeRung.step(five, twoRows))
    }

    @Test
    fun `a cell whose queen empties a unit is left to squeeze`() {
        // 23 would also leave regions 0 and 1 on row 1, but first takes all of row 3; 8 likewise empties row 0.
        val crossed = CountSqueezeRung.steps(five, twoRows).flatMap { excluded(it) }.toSet()
        assertTrue(23 !in crossed && 8 !in crossed)
        val squeezed = SqueezeRung.steps(five, twoRows).flatMap { excluded(it) }.toSet()
        assertTrue(23 in squeezed && 8 in squeezed)
    }

    @Test
    fun `on an N=2 board the regions' missing queens are counted against the rows' room`() {
        // Regions 0 and 1 miss 4 queens on rows 2 and 3 (12, 13, 18, 19); a queen on 17 or 23 leaves those rows room for 3.
        val sixN2 = QueensTestSupport.puzzle(6, queensPerUnit = 2) { _, col -> col }
        val state = QueensCandidates(6, emptySet(), setOf(0, 6, 24, 30, 1, 7, 25, 31))
        val proof = CountSqueezeRung.steps(sixN2, state).single()
        assertEquals(QueensDeduction.Exclude(CountSqueezeRung, region(0), setOf(17, 23)), proof.deduction)
        assertEquals(setOf(12, 13, 18, 19), proof.evidence)
    }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> {
        val text = CountSqueezeRungTest::class.java.getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString()
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

    private fun unitOf(puzzle: QueensPuzzle, kind: HintReason.Kind, cell: Int): Int = when (kind) {
        HintReason.Kind.ROW -> cell / puzzle.size
        HintReason.Kind.COLUMN -> cell % puzzle.size
        HintReason.Kind.REGION -> puzzle.regions[cell]
    }

    @Test
    fun `no cell the rung crosses on a golden board is a solution cell, and evidence is open cells of at least two units`() {
        var fired = 0
        for ((name, puzzle) in goldenBoards()) {
            val solution = puzzle.solution!!.map { it.idx }.toSet()
            for (state in states(puzzle)) for (proof in CountSqueezeRung.steps(puzzle, state)) {
                fired++
                val d = proof.deduction as QueensDeduction.Exclude
                val units = proof.evidence.map { unitOf(puzzle, d.unit.kind, it) }.toSet()
                assertTrue("$name $d", d.cells.none { it in solution })
                assertTrue("$name $d", d.cells.all(state::isOpen))
                assertTrue("$name $d", proof.evidence.all(state::isOpen))
                assertTrue("$name $d", d.cells.intersect(proof.evidence).isEmpty())
                assertTrue("$name $d", units.size >= 2 && units.min() == d.unit.unit)
            }
        }
        assertTrue(fired > 0)
    }
}
