package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The grading mode picks the label on a Unique board (90): the analyzer never sees the mode. */
class ScoredGradingTest {

    private val editor = FakeScoredGrader()
    private val legacy = Difficulty(FakeScoredGrader.LegacyRung)

    private fun Verdict.difficulty(): Difficulty? = (this as Verdict.Unique).difficulty

    @Test
    fun `legacy grading, the default, is the editor's own verdict and never asks the scored grader`() {
        val board = editor.draft(5, 5, 5)

        assertEquals(editor.analyze(board.state.draft, Budget.Unlimited), board.analyze(Budget.Unlimited))
        assertEquals(legacy, board.analyze(Budget.Unlimited, GradingMode.Legacy).difficulty())
        assertTrue(editor.graded.isEmpty())
    }

    @Test
    fun `scored grading replaces the label with the scored grader's and keeps the solution`() {
        val verdict = editor.draft(5, 5, 5).analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique

        assertEquals(Difficulty(Difficulty.Level.Hard, score = 15, hardestRung = FakeScoredGrader.LegacyRung), verdict.difficulty)
        assertEquals(Solution(listOf(5, 5, 5)), verdict.solution)
    }

    @Test
    fun `a board that is not Unique is never graded`() {
        val verdict = editor.draft(5, 0, 5).analyze(Budget.Unlimited, GradingMode.Scored)

        assertTrue(verdict is Verdict.Incomplete)
        assertTrue(editor.graded.isEmpty())
    }

    @Test
    fun `a board that needs guessing keeps that verdict and is not graded`() {
        editor.needsGuessing = true

        val verdict = editor.draft(5, 5, 5).analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique

        assertTrue(verdict.needsGuessing)
        assertNull(verdict.difficulty)
        assertTrue(editor.graded.isEmpty())
    }

    @Test
    fun `a scored grader that cannot grade the board keeps the legacy label`() {
        editor.gradeable = false
        val board = editor.draft(5, 5, 5)

        val verdict = board.analyze(Budget.Unlimited, GradingMode.Scored)

        assertEquals(listOf(listOf(5, 5, 5)), editor.graded)
        assertEquals(legacy, verdict.difficulty())
    }

    @Test
    fun `an editor without a scored grader keeps its legacy label in scored mode`() {
        val board = EditorDraft(LegacyOnly, DraftState(listOf(5, 5, 5)))

        assertEquals(legacy, board.analyze(Budget.Unlimited, GradingMode.Scored).difficulty())
    }

    private object LegacyOnly : GameEditor<List<Int>> {
        override val gameId = GameId("legacy")
        override val variants = listOf(VariantId("legacy/n3"))
        override val draftFormatVersion = 1
        override fun newDraft(variant: VariantId): List<Int> = List(3) { 0 }
        override fun variantOf(draft: List<Int>) = variants.single()
        override fun encodeDraft(state: DraftState<List<Int>>) = ByteArray(0)
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> = DecodeResult.Ok(DraftState(emptyList()))
        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Unique(Solution(draft), Difficulty(FakeScoredGrader.LegacyRung))
    }
}
