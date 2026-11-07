package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoDraftAnalyzerTest {

    private fun analyze(draft: TangoDraft) = TangoDraftAnalyzer.analyze(draft, Budget.Unlimited)

    @Test
    fun `an empty board is Incomplete`() {
        assertEquals(
            Verdict.Incomplete(IncompleteReason(TangoDraftAnalyzer.EMPTY, 0)),
            analyze(TangoDraft(emptyMap(), emptyList())),
        )
    }

    @Test
    fun `the sample board is Unique with one ordinal per cell`() {
        val sample = TangoTestSupport.sample

        val verdict = analyze(TangoDraft(sample.givens, sample.markers))

        assertEquals(Verdict.Unique(Solution(TangoTestSupport.solution.map { it.ordinal })), verdict)
    }

    @Test
    fun `a single given is Multiple with the cells the two solutions differ on`() {
        val verdict = analyze(TangoDraft(mapOf(0 to TangoCell.SUN), emptyList()))

        val multiple = verdict as Verdict.Multiple
        val a = multiple.solutionA.values
        val b = multiple.solutionB.values
        assertNotEquals(a, b)
        assertEquals(a.indices.filter { a[it] != b[it] }.toSet(), multiple.differingCells)
        assertTrue(0 !in multiple.differingCells)
    }

    @Test
    fun `three suns in a row is None with those cells`() {
        val draft = TangoDraft((0..2).associateWith { TangoCell.SUN }, emptyList())

        assertEquals(Verdict.None(setOf(0, 1, 2)), analyze(draft))
    }

    @Test
    fun `a spent budget is Undetermined`() {
        assertEquals(Verdict.Undetermined, TangoDraftAnalyzer.analyze(TangoDraft(mapOf(0 to TangoCell.SUN), emptyList())) { true })
    }
}
