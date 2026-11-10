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
import org.junit.Assert.assertTrue
import org.junit.Test

/** D7: a band whose inside units leave it one queen crosses what attacks all of the leftover set. Crafted boards. */
class LeftoverQueenRungTest {

    /** Rows 0–1 hold region 0 and the leftover set {4, 9} of region 1. Rows 2–3 hold regions 2 and 3 wholly. */
    private val twoRows = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 0, 0, 1,
            0, 0, 0, 0, 1,
            2, 2, 3, 3, 1,
            2, 2, 3, 3, 1,
            4, 4, 4, 4, 4,
        ),
    )

    /** Regions 0 and 1 hold row 0 wholly, plus the leftover set {5, 9}. */
    private val twoRegions = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 0, 1, 1,
            0, 2, 2, 2, 1,
            3, 3, 2, 4, 4,
            3, 3, 2, 4, 4,
            3, 3, 3, 4, 4,
        ),
    )

    /** Rows 0–2 hold regions 0 and 1 wholly, plus the leftover set {5, 11, 17} of region 2. */
    private val threeRows = QueensPuzzle(
        size = 6,
        regions = listOf(
            0, 0, 0, 1, 1, 2,
            0, 0, 1, 1, 1, 2,
            0, 0, 1, 1, 1, 2,
            3, 3, 3, 4, 4, 2,
            3, 3, 4, 4, 5, 5,
            3, 3, 4, 4, 5, 5,
        ),
    )

    /** Rows 0–3 hold regions 0, 1 and 2 wholly, plus the leftover set {4, 9, 14, 19} in column 4. */
    private val fourRows = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 1, 2, 2, 3,
            0, 1, 2, 2, 3,
            0, 1, 2, 2, 3,
            0, 1, 2, 2, 3,
            4, 4, 4, 4, 3,
        ),
    )

    /** The same shape one size up: rows 0–4 hold regions 0–3 wholly, a band of five. */
    private val fiveRows = QueensPuzzle(
        size = 6,
        regions = listOf(
            0, 1, 2, 3, 3, 4,
            0, 1, 2, 3, 3, 4,
            0, 1, 2, 3, 3, 4,
            0, 1, 2, 3, 3, 4,
            0, 1, 2, 3, 3, 4,
            5, 5, 5, 5, 5, 4,
        ),
    )

    /** 6×6, two queens per unit, each column its own region. */
    private val sixN2 = QueensTestSupport.puzzle(6, queensPerUnit = 2) { _, col -> col }

    private fun empty(size: Int) = QueensCandidates(size, emptySet(), emptySet())
    private fun proof(kind: HintReason.Kind, unit: Int, cells: Set<Int>, evidence: Set<Int>) =
        QueensProof(QueensDeduction.Exclude(HintReason(kind, unit, LeftoverQueenRung), cells), evidence)
    private fun rows(size: Int, vararg rows: Int): Set<Int> = rows.flatMap { r -> (0 until size).map { r * size + it } }.toSet()
    private fun cells(proof: QueensProof) = (proof.deduction as QueensDeduction.Exclude).cells

    @Test
    fun `the rung is the hard leftover-queen rung`() {
        assertEquals("leftover-queen", LeftoverQueenRung.id)
        assertEquals(Difficulty.Level.Hard, LeftoverQueenRung.level)
        assertEquals("queens.hint.rung.leftover-queen", LeftoverQueenRung.captionKey)
    }

    @Test
    fun `a two-row band with one region inside crosses the cells attacking both leftover cells, naming the top row`() {
        val proofs = LeftoverQueenRung.steps(twoRows, empty(5)).toList()
        // 3 and 8 touch both 4 and 9; 14, 19 and 24 share their column. 13 touches only 9. 3 and 8 sit in
        // the band, so they leave the evidence: a cell is never both reason and target.
        assertTrue(proof(HintReason.Kind.ROW, 0, setOf(3, 8, 14, 19, 24), rows(5, 0, 1) - setOf(3, 8)) in proofs)
    }

    @Test
    fun `two regions with one row inside cross the cells attacking both leftover cells, naming the lower region`() {
        val proofs = LeftoverQueenRung.steps(twoRegions, empty(5)).toList()
        // 5 and 9 share only row 1.
        assertTrue(proof(HintReason.Kind.REGION, 0, setOf(6, 7, 8), setOf(0, 1, 2, 3, 4, 5, 9)) in proofs)
    }

    @Test
    fun `a three-row band with two regions inside crosses what attacks all three leftover cells`() {
        val proofs = LeftoverQueenRung.steps(threeRows, empty(6)).toList()
        // 10 touches 5, 11 and 17; 23, 29 and 35 share their column. 4 and 16 touch only two of them.
        assertTrue(proof(HintReason.Kind.ROW, 0, setOf(10, 23, 29, 35), rows(6, 0, 1, 2) - 10) in proofs)
    }

    @Test
    fun `a four-row band is searched, a five-row band is not`() {
        assertTrue(proof(HintReason.Kind.ROW, 0, setOf(24), rows(5, 0, 1, 2, 3)) in LeftoverQueenRung.steps(fourRows, empty(5)).toList())
        val band = rows(6, 0, 1, 2, 3, 4)
        assertTrue(LeftoverQueenRung.steps(fiveRows, empty(6)).none { it.evidence == band - cells(it) })
    }

    @Test
    fun `an empty leftover set yields nothing`() {
        // With 4 and 9 crossed, rows 0–1 are open only in region 0.
        val state = QueensCandidates(5, emptySet(), setOf(4, 9))
        val band = rows(5, 0, 1) - setOf(4, 9)
        assertTrue(LeftoverQueenRung.steps(twoRows, state).none { it.evidence == band - cells(it) })
    }

    @Test
    fun `a two-row band with two regions inside yields nothing`() {
        assertTrue(LeftoverQueenRung.steps(twoRows, empty(5)).none { it.evidence == rows(5, 2, 3) - cells(it) })
    }

    @Test
    fun `on an N=2 board a shared column rules a cell out only when it misses one queen`() {
        // Rows 0–1 miss four queens; column-regions 0 (missing two) and 1 (queen on 25, missing one) are open
        // only there, so the leftover set {4, 10} holds one queen. Every cell touching both is crossed already.
        val excluded = setOf(2, 3, 5, 8, 9, 11, 12, 18, 24, 30, 13, 19, 31)
        val band = setOf(0, 1, 4, 6, 7, 10)
        val open = LeftoverQueenRung.steps(sixN2, QueensCandidates(6, setOf(25), excluded)).toList()
        assertTrue(open.none { it.evidence == band - cells(it) })

        // A queen on 28 leaves column 4 one queen short: a queen anywhere else in it would fill it.
        val filling = LeftoverQueenRung.steps(sixN2, QueensCandidates(6, setOf(25, 28), excluded)).toList()
        assertTrue(proof(HintReason.Kind.ROW, 0, setOf(16, 22, 34), band) in filling)
    }

    @Test
    fun `proofs come smallest k, then lowest crossed cell, then lowest unit, and step is the first`() {
        val proofs = LeftoverQueenRung.steps(fourRows, empty(5)).toList()
        assertTrue(proofs.size >= 2)
        val keys = proofs.map { p -> Triple(band(5, fourRows, p).size, cells(p).min(), p.deduction.unit.kind.ordinal * 100 + p.deduction.unit.unit) }
        assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second }, { it.third })), keys)
        assertEquals(proofs.first().deduction, LeftoverQueenRung.step(fourRows, empty(5)))
    }

    /** The band a proof's evidence covers: units of the reason's kind. */
    private fun band(size: Int, puzzle: QueensPuzzle, proof: QueensProof): Set<Int> = proof.evidence.map(unitOf(size, puzzle, proof)).toSet()

    private fun unitOf(size: Int, puzzle: QueensPuzzle, proof: QueensProof): (Int) -> Int = when (proof.deduction.unit.kind) {
        HintReason.Kind.ROW -> { c -> c / size }
        HintReason.Kind.COLUMN -> { c -> c % size }
        HintReason.Kind.REGION -> puzzle.regions::get
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

    @Test
    fun `no cell the rung crosses on a golden board is a solution cell, and evidence is the band's open cells less the crossed ones`() {
        var fired = 0
        for ((name, puzzle) in goldenBoards()) {
            val n = puzzle.size
            val solution = puzzle.solution!!.map { it.idx }.toSet()
            for (state in states(puzzle)) for (proof in LeftoverQueenRung.steps(puzzle, state)) {
                fired++
                val crossed = cells(proof)
                assertTrue("$name $proof", crossed.none { it in solution })
                assertTrue("$name $proof", crossed.all(state::isOpen))
                val unitOf = unitOf(n, puzzle, proof)
                val band = band(n, puzzle, proof)
                assertTrue("$name $proof", band.size in 2..4)
                assertEquals("$name $proof", band.min(), proof.deduction.unit.unit)
                assertEquals("$name $proof", (0 until n * n).filter { state.isOpen(it) && unitOf(it) in band && it !in crossed }.toSet(), proof.evidence)
            }
        }
        assertTrue(fired > 0)
    }
}
