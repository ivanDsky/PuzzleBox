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

/** D6: k rows confined to k columns (or the reverse) take those lines' queens. Crafted states. */
class LineGroupPigeonholeRungTest {

    /** 5×5, one queen per unit; D6 reads no regions, so these only matter to [PigeonholeRung]. */
    private val five = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 1, 1, 1,
            0, 0, 1, 2, 2,
            3, 0, 1, 2, 2,
            3, 3, 4, 4, 2,
            3, 3, 4, 4, 4,
        ),
    )

    /** 6×6, two queens per unit, each column its own region. */
    private val sixN2 = QueensTestSupport.puzzle(6, queensPerUnit = 2) { _, col -> col }

    private fun column(index: Int) = HintReason(HintReason.Kind.COLUMN, index, LineGroupPigeonholeRung)
    private fun row(index: Int) = HintReason(HintReason.Kind.ROW, index, LineGroupPigeonholeRung)
    private fun state(size: Int, excluded: Set<Int>, queens: Set<Int> = emptySet()) = QueensCandidates(size, queens, excluded)
    private fun cells(proof: QueensProof) = (proof.deduction as QueensDeduction.Exclude).cells

    /** A COLUMN reason means rows were confined, so the evidence is read as rows; a ROW reason, as columns. */
    private fun lineOf(size: Int, proof: QueensProof): (Int) -> Int =
        if (proof.deduction.unit.kind == HintReason.Kind.COLUMN) { c -> c / size } else { c -> c % size }

    private fun confinedLines(size: Int, proof: QueensProof): Set<Int> = proof.evidence.map(lineOf(size, proof)).toSet()

    @Test
    fun `the rung is the hard line-group pigeonhole rung`() {
        assertEquals("line-group-pigeonhole", LineGroupPigeonholeRung.id)
        assertEquals(Difficulty.Level.Hard, LineGroupPigeonholeRung.level)
        assertEquals("queens.hint.rung.line-group-pigeonhole", LineGroupPigeonholeRung.captionKey)
    }

    @Test
    fun `two rows open only in two columns cross the rest of those columns, naming the lowest column`() {
        // Rows 0 and 2 are open only in columns 1 and 3.
        val proofs = LineGroupPigeonholeRung.steps(five, state(5, setOf(0, 2, 4, 10, 12, 14))).toList()
        val expected = QueensProof(
            QueensDeduction.Exclude(column(1), setOf(6, 8, 16, 18, 21, 23)),
            setOf(1, 3, 11, 13),
        )
        assertEquals(listOf(expected), proofs)
    }

    @Test
    fun `two columns open only in two rows cross the rest of those rows, naming the lowest row`() {
        // Columns 0 and 2 are open only in rows 1 and 3.
        val proofs = LineGroupPigeonholeRung.steps(five, state(5, setOf(0, 10, 20, 2, 12, 22))).toList()
        val expected = QueensProof(
            QueensDeduction.Exclude(row(1), setOf(6, 8, 9, 16, 18, 19)),
            setOf(5, 7, 15, 17),
        )
        assertEquals(listOf(expected), proofs)
    }

    @Test
    fun `a pair holding a row already confined on its own yields nothing`() {
        // Row 0 is open only at cell 1 (column 1); row 2 only in columns 1 and 3.
        val excluded = setOf(0, 2, 3, 4, 10, 12, 14)
        assertEquals(emptyList<QueensProof>(), LineGroupPigeonholeRung.steps(five, state(5, excluded)).toList())
    }

    @Test
    fun `a set holding more than half the missing queens is left to its complement`() {
        // Rows 0, 2 and 4 open only in columns 0, 2 and 4: the same cells as columns 1 and 3 confined to rows 1 and 3.
        val excluded = setOf(1, 3, 11, 13, 21, 23)
        val proofs = LineGroupPigeonholeRung.steps(five, state(5, excluded)).toList()
        assertEquals(listOf(row(1)), proofs.map { it.deduction.unit })
        assertEquals(setOf(5, 7, 9, 15, 17, 19), cells(proofs.single()))
    }

    @Test
    fun `no exclusion is also found by the region pigeonhole, and a region-line confinement yields nothing`() {
        val xWing = state(5, setOf(0, 2, 4, 10, 12, 14))
        val pigeonhole = PigeonholeRung.steps(five, xWing).map(::cells).toSet()
        val lineGroup = LineGroupPigeonholeRung.steps(five, xWing).map(::cells).toSet()
        assertTrue(lineGroup.isNotEmpty())
        assertTrue(pigeonhole.intersect(lineGroup).isEmpty())

        // Regions 0 and 1 fill rows 0–1 but for cells 4 and 9: the region pigeonhole fires, D6 does not.
        val regionBand = QueensPuzzle(5, listOf(0, 0, 1, 1, 2, 0, 0, 1, 1, 2, 3, 3, 4, 4, 2, 3, 3, 4, 4, 4, 3, 3, 4, 4, 4))
        val empty = state(5, emptySet())
        assertTrue(PigeonholeRung.steps(regionBand, empty).any())
        assertEquals(emptyList<QueensProof>(), LineGroupPigeonholeRung.steps(regionBand, empty).toList())
    }

    @Test
    fun `on an N=2 board confinement is judged on missing queens, not on lines`() {
        // Rows 0 and 1 (four missing queens) are open only in columns 0, 2 and 4. With queens
        // on 20 and 34, those columns miss 2 + 1 + 1 = 4: three columns take the rows' four queens.
        val excluded = setOf(1, 3, 5, 7, 9, 11)
        val evidence = setOf(0, 2, 4, 6, 8, 10)
        val withQueens = LineGroupPigeonholeRung.steps(sixN2, state(6, excluded, queens = setOf(20, 34))).toList()
        val proof = withQueens.single { it.evidence == evidence }
        assertEquals(column(0), proof.deduction.unit)
        assertEquals(setOf(12, 14, 16, 18, 22, 24, 26, 28, 30, 32), cells(proof))

        // Without them the three columns miss six queens: no confinement.
        val bare = LineGroupPigeonholeRung.steps(sixN2, state(6, excluded)).toList()
        assertTrue(bare.none { it.evidence == evidence })
    }

    @Test
    fun `proofs come smallest k, then lowest crossed cell, then lowest unit, and step is the first`() {
        // Columns 0 and 2 confined to rows 1 and 3, and (k = 2 again) rows 0 and 4 confined to columns 1 and 3.
        val st = state(5, setOf(0, 10, 20, 2, 12, 22, 4, 24))
        val proofs = LineGroupPigeonholeRung.steps(five, st).toList()
        assertTrue(proofs.size >= 2)
        val keys = proofs.map { p -> Triple(confinedLines(5, p).size, cells(p).min(), p.deduction.unit.kind.ordinal * 100 + p.deduction.unit.unit) }
        assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second }, { it.third })), keys)
        assertEquals(proofs.first().deduction, LineGroupPigeonholeRung.step(five, st))
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
    fun `no cell the rung crosses on a golden board is a solution cell, and evidence is the confined lines' open cells`() {
        var fired = 0
        for ((name, puzzle) in goldenBoards()) {
            val n = puzzle.size
            val solution = puzzle.solution!!.map { it.idx }.toSet()
            for (state in states(puzzle)) for (proof in LineGroupPigeonholeRung.steps(puzzle, state)) {
                fired++
                val crossed = cells(proof)
                assertTrue("$name $proof", crossed.none { it in solution })
                assertTrue("$name $proof", crossed.all(state::isOpen))
                assertTrue("$name $proof", crossed.intersect(proof.evidence).isEmpty())
                val lineOf = lineOf(n, proof)
                val lines = confinedLines(n, proof)
                assertTrue("$name $proof", lines.size >= 2)
                assertEquals("$name $proof", (0 until n * n).filter { state.isOpen(it) && lineOf(it) in lines }.toSet(), proof.evidence)
            }
        }
        assertTrue(fired > 0)
    }
}
