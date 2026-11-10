package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.LineLockRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.SinglesRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep.Deduction.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueensSpotlightMapperTest {

    private val puzzle = QueensPuzzle(4, List(16) { (it / 4) })
    private val row = HintReason(HintReason.Kind.ROW, 1)

    private fun cells(vararg idx: Int) = idx.mapTo(LinkedHashSet()) { Cell(it) }

    private fun map(step: QueensStep, playId: Int = 0) = QueensSpotlightMapper.map(step, puzzle, playId)

    private fun deduction(evidence: Set<Cell>, result: Result, rung: com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung = SinglesRung) =
        QueensStep.Deduction(rung, row, evidence, result)

    @Test
    fun `a rule break marks one culprit and the queen it attacks as evidence`() {
        val spotlight = map(QueensStep.Mistake(Cell(5), QueensStep.Mistake.Kind.RULE_BREAK, Cell(10)))!!

        assertEquals(mapOf(5 to SpotlightRole.CULPRIT, 10 to SpotlightRole.EVIDENCE), spotlight.roles)
        assertEquals(setOf(5, 10), spotlight.callout!!.anchor)
    }

    @Test
    fun `an off-solution queen and a wrong cross light only the culprit`() {
        for (kind in listOf(QueensStep.Mistake.Kind.OFF_SOLUTION, QueensStep.Mistake.Kind.WRONG_CROSS)) {
            assertEquals(kind.name, mapOf(3 to SpotlightRole.CULPRIT), map(QueensStep.Mistake(Cell(3), kind, null))!!.roles)
        }
    }

    @Test
    fun `a cross shows its proof as evidence and its cells excluded, with no target`() {
        val spotlight = map(deduction(cells(0, 1), Result.Cross(cells(2, 3))))!!

        assertEquals(
            mapOf(0 to SpotlightRole.EVIDENCE, 1 to SpotlightRole.EVIDENCE, 2 to SpotlightRole.EXCLUDED, 3 to SpotlightRole.EXCLUDED),
            spotlight.roles,
        )
        assertEquals(setOf(0, 1), spotlight.callout!!.anchor)
    }

    @Test
    fun `a cell that is both evidence and crossed is excluded, once`() {
        val spotlight = map(deduction(cells(0, 1), Result.Cross(cells(1, 2))))!!

        assertEquals(SpotlightRole.EXCLUDED, spotlight.roles[1])
        assertEquals(setOf(0, 1, 2), spotlight.roles.keys)
    }

    @Test
    fun `a cross with no proof anchors on the excluded cells`() {
        val spotlight = map(deduction(emptySet(), Result.Cross(cells(2, 3))))!!

        assertEquals(setOf(2, 3), spotlight.callout!!.anchor)
    }

    @Test
    fun `a last cell is the target and the evidence without it stays evidence`() {
        val spotlight = map(deduction(cells(4, 5, 6), Result.LastCell(Cell(5))))!!

        assertEquals(
            mapOf(4 to SpotlightRole.EVIDENCE, 5 to SpotlightRole.TARGET, 6 to SpotlightRole.EVIDENCE),
            spotlight.roles,
        )
        assertEquals(setOf(4, 5, 6), spotlight.callout!!.anchor)
    }

    @Test
    fun `a reveal excludes a cell that holds no queen`() {
        assertEquals(mapOf(7 to SpotlightRole.EXCLUDED), map(QueensStep.Reveal(Cell(7)))!!.roles)
    }

    @Test
    fun `a solved board and an unavailable step show nothing and count nothing`() {
        assertNull(map(QueensStep.Solved))
        assertNull(map(QueensStep.Unavailable))
        assertNull(QueensSpotlightMapper.keyOf(QueensStep.Solved))
        assertNull(QueensSpotlightMapper.keyOf(QueensStep.Unavailable))
    }

    @Test
    fun `the spotlight is board sized, carries the play id and the step's copy`() {
        val step = QueensStep.Reveal(Cell(7))
        val spotlight = map(step, playId = 9)!!

        assertEquals(9, spotlight.playId)
        assertEquals(4, spotlight.rows)
        assertEquals(4, spotlight.cols)
    }

    @Test
    fun `a key ignores the order of the crossed cells and tells steps apart`() {
        fun key(step: QueensStep) = QueensSpotlightMapper.keyOf(step)
        val base = deduction(cells(0), Result.Cross(cells(1, 2, 3)))

        assertNotEquals(key(base), key(deduction(cells(0), Result.Cross(cells(1, 2)))))
        assertNotEquals(key(base), key(deduction(cells(0), Result.Cross(cells(1, 2, 3)), rung = LineLockRung)))
        assertNotEquals(
            key(QueensStep.Mistake(Cell(1), QueensStep.Mistake.Kind.OFF_SOLUTION, null)),
            key(QueensStep.Mistake(Cell(1), QueensStep.Mistake.Kind.WRONG_CROSS, null)),
        )
    }
}
