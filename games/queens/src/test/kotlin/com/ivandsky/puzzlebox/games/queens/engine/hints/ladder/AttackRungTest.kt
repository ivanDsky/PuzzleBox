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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D0: the crosses a standing queen's reach is still missing, as with Auto-cross off. */
class AttackRungTest {

    /** Region 2 reaches cell 19, which shares no line with cell 12 and does not touch it. */
    private val five = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 0, 1, 1,
            0, 2, 2, 2, 1,
            3, 2, 2, 2, 2,
            3, 3, 3, 4, 2,
            3, 3, 4, 4, 4,
        ),
    )

    /** 6×6, two queens per unit, each column its own region. */
    private val sixN2 = QueensTestSupport.puzzle(6, queensPerUnit = 2) { _, col -> col }

    private fun row(index: Int) = HintReason(HintReason.Kind.ROW, index, AttackRung)

    private fun excluded(proof: QueensProof): Set<Int> = (proof.deduction as QueensDeduction.Exclude).cells

    @Test
    fun `the rung is the easy attack rung`() {
        assertEquals("attack", AttackRung.id)
        assertEquals(Difficulty.Level.Easy, AttackRung.level)
        assertEquals("queens.hint.rung.attack", AttackRung.captionKey)
    }

    @Test
    fun `a queen seeded without side exclusions lists its row, column, region and neighbours`() {
        val state = QueensCandidates(5, setOf(12), emptySet())
        val proof = AttackRung.steps(five, state).single()
        assertEquals(
            QueensDeduction.Exclude(AttackRung, row(2), setOf(2, 6, 7, 8, 10, 11, 13, 14, 16, 17, 18, 19, 22)),
            proof.deduction,
        )
        assertEquals(setOf(12), proof.evidence)
    }

    @Test
    fun `a cross the player already made is left off the list`() {
        val state = QueensCandidates(5, setOf(12), setOf(19))
        assertEquals(setOf(2, 6, 7, 8, 10, 11, 13, 14, 16, 17, 18, 22), excluded(AttackRung.steps(five, state).single()))
    }

    @Test
    fun `with Auto-cross on the queen's reach is already crossed, so the rung is silent`() {
        assertTrue(AttackRung.steps(five, QueensCandidates.start(five, setOf(12))).none())
        assertNull(AttackRung.step(five, QueensCandidates.start(five, setOf(12))))
        assertTrue(AttackRung.steps(sixN2, QueensCandidates.start(sixN2, setOf(14, 17))).none())
    }

    @Test
    fun `on an N=2 board a unit is crossed only once it holds both its queens`() {
        val alone = AttackRung.steps(sixN2, QueensCandidates(6, setOf(14), emptySet())).single()
        assertEquals(setOf(7, 8, 9, 13, 15, 19, 20, 21), excluded(alone))

        val fullRow = AttackRung.steps(sixN2, QueensCandidates(6, setOf(14, 17), emptySet())).toList()
        assertEquals(listOf(setOf(14), setOf(17)), fullRow.map { it.evidence })
        assertEquals(setOf(7, 8, 9, 12, 13, 15, 16, 19, 20, 21), excluded(fullRow[0]))
        assertEquals(setOf(10, 11, 12, 13, 15, 16, 22, 23), excluded(fullRow[1]))

        val fullColumn = AttackRung.steps(sixN2, QueensCandidates(6, setOf(14, 26), emptySet())).first()
        assertEquals(setOf(2, 7, 8, 9, 13, 15, 19, 20, 21, 32), excluded(fullColumn))
    }

    @Test
    fun `queens are taken in ascending cell order, one exclusion each, and step is the first`() {
        val state = QueensCandidates(5, linkedSetOf(18, 1), emptySet())
        val proofs = AttackRung.steps(five, state).toList()
        assertEquals(listOf(setOf(1), setOf(18)), proofs.map { it.evidence })
        assertEquals(listOf(row(0), row(3)), proofs.map { it.deduction.unit })
        assertEquals(proofs.first().deduction, AttackRung.step(five, state))
    }

    private fun goldenBoards(): List<Pair<String, QueensPuzzle>> {
        val text = AttackRungTest::class.java.getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString()
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
    fun `no cell the rung crosses on a golden board is a solution cell`() {
        var fired = 0
        for ((name, puzzle) in goldenBoards()) {
            val solution = puzzle.solution!!.map { it.idx }.toSet()
            for (state in states(puzzle)) for (proof in AttackRung.steps(puzzle, state)) {
                fired++
                assertTrue("$name ${proof.deduction}", excluded(proof).none { it in solution })
                assertTrue("$name ${proof.deduction}", excluded(proof).all(state::isOpen))
            }
        }
        assertTrue(fired > 0)
    }
}
