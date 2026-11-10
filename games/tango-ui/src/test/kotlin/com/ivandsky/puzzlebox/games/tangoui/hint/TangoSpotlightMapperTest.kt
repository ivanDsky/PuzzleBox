package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TangoSpotlightMapperTest {

    private val puzzle: TangoPuzzle = TangoPlayFixture.puzzle
    private val grid: List<TangoCell?> = List(puzzle.cellCount) { puzzle.givens[it] }

    private fun deduction(
        rung: TangoStepRung = TangoStepRung.PAIR,
        idx: Int = 2,
        value: TangoCell = TangoCell.MOON,
        evidence: Set<Int> = setOf(0, 1),
    ) = TangoStep.Deduction(rung, idx, value, evidence, row = null, col = null, options = null)

    private fun map(step: TangoStep, board: List<TangoCell?> = grid, playId: Int = 3) =
        TangoSpotlightMapper.map(step, puzzle, board, playId)

    @Test
    fun `a deduction lights its target and its evidence and nothing else`() {
        val spotlight = map(deduction(idx = 2, evidence = setOf(0, 1)))

        assertEquals(
            mapOf(2 to SpotlightRole.TARGET, 0 to SpotlightRole.EVIDENCE, 1 to SpotlightRole.EVIDENCE),
            spotlight.roles,
        )
    }

    @Test
    fun `a cell that is both target and evidence stays the target`() {
        val spotlight = map(deduction(rung = TangoStepRung.SIGN, idx = 3, evidence = setOf(3, 9)))

        assertEquals(mapOf(3 to SpotlightRole.TARGET, 9 to SpotlightRole.EVIDENCE), spotlight.roles)
        assertEquals(setOf(9), spotlight.callout!!.anchor)
    }

    @Test
    fun `the count squeeze draws its what-if cells struck out`() {
        val spotlight = map(deduction(rung = TangoStepRung.COUNT_SQUEEZE, idx = 24, evidence = setOf(12, 18)))

        assertEquals(
            mapOf(24 to SpotlightRole.TARGET, 12 to SpotlightRole.EXCLUDED, 18 to SpotlightRole.EXCLUDED),
            spotlight.roles,
        )
    }

    @Test
    fun `a reveal lights only its cell as the target`() {
        assertEquals(mapOf(5 to SpotlightRole.TARGET), map(TangoStep.Reveal(5, TangoCell.SUN)).roles)
    }

    @Test
    fun `a mistake lights only the wrong cell, with or without a reason`() {
        for (reason in listOf(null, TangoVerdict.Reason.ThreeInARow)) {
            assertEquals(mapOf(8 to SpotlightRole.CULPRIT), map(TangoStep.Mistake(8, reason)).roles)
        }
    }

    @Test
    fun `a solved board and an unavailable step have no key`() {
        for (step in listOf(TangoStep.Solved, TangoStep.Unavailable)) assertNull(TangoSpotlightMapper.keyOf(step))
    }

    @Test
    fun `a step with no key is refused, not mapped to an empty spotlight`() {
        for (step in listOf(TangoStep.Solved, TangoStep.Unavailable)) {
            assertThrows(step.toString(), IllegalStateException::class.java) { map(step) }
        }
    }

    @Test
    fun `the spotlight is n by n and carries the play id`() {
        val spotlight = map(deduction(), playId = 7)

        assertEquals(puzzle.n, spotlight.rows)
        assertEquals(puzzle.n, spotlight.cols)
        assertEquals(7, spotlight.playId)
    }

    @Test
    fun `the callout sits by the evidence, or by the target when there is none`() {
        assertEquals(setOf(0, 1), map(deduction(evidence = setOf(0, 1))).callout!!.anchor)
        assertEquals(setOf(2), map(deduction(evidence = emptySet())).callout!!.anchor)
        assertEquals(setOf(5), map(TangoStep.Reveal(5, TangoCell.SUN)).callout!!.anchor)
        assertEquals(setOf(8), map(TangoStep.Mistake(8, null)).callout!!.anchor)
    }

    @Test
    fun `a mistake carries the entry read from the grid`() {
        val board = grid.toMutableList().also { it[8] = TangoCell.MOON }
        assertEquals(TangoSpotlightGlyphs(6, mapOf(8 to TangoCell.MOON)), map(TangoStep.Mistake(8, null), board).glyphs)
    }

    @Test
    fun `a different rung or evidence is a different key`() {
        val base = TangoSpotlightMapper.keyOf(deduction())

        assertNotEquals(base, TangoSpotlightMapper.keyOf(deduction(rung = TangoStepRung.SANDWICH)))
        assertNotEquals(base, TangoSpotlightMapper.keyOf(deduction(evidence = setOf(0, 4))))
    }

    @Test
    fun `mistakes on different cells differ and a mistake is not a reveal on the same cell`() {
        assertNotEquals(
            TangoSpotlightMapper.keyOf(TangoStep.Mistake(1, null)),
            TangoSpotlightMapper.keyOf(TangoStep.Mistake(2, null)),
        )
        assertNotEquals(
            TangoSpotlightMapper.keyOf(TangoStep.Mistake(1, null)),
            TangoSpotlightMapper.keyOf(TangoStep.Reveal(1, TangoCell.SUN)),
        )
        assertEquals(
            TangoSpotlightMapper.keyOf(TangoStep.Mistake(1, null)),
            TangoSpotlightMapper.keyOf(TangoStep.Mistake(1, TangoVerdict.Reason.CountExceeded)),
        )
    }
}
