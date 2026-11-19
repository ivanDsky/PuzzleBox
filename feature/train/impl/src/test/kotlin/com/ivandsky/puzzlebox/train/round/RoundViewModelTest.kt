package com.ivandsky.puzzlebox.train.round

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerProgressRepository
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerPuzzleSource
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.FakeTrainerGame
import com.ivandsky.puzzlebox.train.InlineDispatcherProvider
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import com.ivandsky.puzzlebox.train.techniques
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoundViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val queens = GameId("queens")
    private val technique = TechniqueId("line-lock")
    private val progress = FakeTrainerProgressRepository()

    private fun round(puzzles: Int, perPuzzle: Int = 5): Pair<RoundViewModel, FakeTrainerGame> {
        val game = FakeTrainerGame(queens, techniques("line-lock"), perPuzzle)
        val source = FakeTrainerPuzzleSource(mapOf(queens to List(puzzles) { TestGames.puzzle(queens, it.toByte()) }))
        return RoundViewModel(game, technique, source, RecordAnswer(progress), InlineDispatcherProvider) to game
    }

    private fun RoundViewModel.started(attempt: Int = 1): RoundViewModel = also { start(attempt) }

    private val RoundViewModel.question: RoundState.Question get() = state.value as RoundState.Question

    private fun RoundViewModel.verdict(verdict: TrainerVerdict) = onVerdict(question.snippet, verdict)

    private fun stored(): TechniqueProgress? = progress.stored[queens]?.get(technique)

    private suspend fun seedRow(row: Int) {
        progress.update(queens, technique) { TechniqueProgress(Mark.Practicing, row, 1) }
    }

    @Test
    fun `a Right answer raises the in-a-row count, marks Practicing and counts as right`() {
        val vm = round(puzzles = 3).first.started()

        vm.verdict(TrainerVerdict.Right)

        assertEquals(Mark.Practicing, stored()?.mark)
        assertEquals(1, stored()?.inRow)
        assertEquals(1, vm.question.right)
        assertTrue(vm.question.answered)
    }

    @Test
    fun `a Wrong answer resets the in-a-row count and is not counted right`() = runTest {
        seedRow(3)
        val vm = round(puzzles = 3).first.started()

        vm.verdict(TrainerVerdict.Wrong)

        assertEquals(0, stored()?.inRow)
        assertEquals(0, vm.question.right)
    }

    @Test
    fun `True but another way keeps the in-a-row count and is not counted right`() = runTest {
        seedRow(3)
        val vm = round(puzzles = 3).first.started()

        vm.verdict(TrainerVerdict.AnotherWay)

        assertEquals(3, stored()?.inRow)
        assertEquals(0, vm.question.right)
    }

    @Test
    fun `Show me records the answer, resets the count, leaves the mark and is not counted right`() = runTest {
        seedRow(3)
        val vm = round(puzzles = 3).first.started()

        vm.showMe(vm.question.snippet)

        assertEquals(0, stored()?.inRow)
        assertEquals(Mark.Practicing, stored()?.mark)
        assertEquals(RecordAnswer.Answer.ShowMe, vm.question.answer)
        assertEquals(0, vm.question.right)
    }

    @Test
    fun `Show me on a technique never answered leaves it New`() {
        val vm = round(puzzles = 3).first.started()

        vm.showMe(vm.question.snippet)

        assertEquals(Mark.New, stored()?.mark)
    }

    @Test
    fun `a second answer on the same snippet is ignored, a verdict or Show me`() {
        val vm = round(puzzles = 3).first.started()
        val snippet = vm.question.snippet

        vm.onVerdict(snippet, TrainerVerdict.Wrong)
        vm.onVerdict(snippet, TrainerVerdict.Right)
        vm.showMe(snippet)

        assertEquals(RecordAnswer.Answer.Verdict(TrainerVerdict.Wrong), vm.question.answer)
        assertEquals(0, vm.question.right)
        assertEquals("one answer stored", 0, stored()?.inRow)
        assertEquals(Mark.Practicing, stored()?.mark)
    }

    @Test
    fun `an answer for a snippet that is no longer up is ignored`() {
        val vm = round(puzzles = 3).first.started()
        val first = vm.question.snippet
        vm.onVerdict(first, TrainerVerdict.Right)
        vm.next()

        vm.onVerdict(first, TrainerVerdict.Right)

        assertNull(vm.question.answer)
        assertEquals("only the first Right was stored", 1, stored()?.inRow)
        assertEquals(1, vm.question.right)
    }

    @Test
    fun `Next before an answer does nothing`() {
        val vm = round(puzzles = 3).first.started()

        vm.next()

        assertEquals(0, vm.question.index)
    }

    @Test
    fun `a round takes at most two snippets from one puzzle and stops at five`() {
        val (vm, game) = round(puzzles = 10, perPuzzle = 5)

        vm.start(1)

        assertEquals(5, vm.question.total)
        assertEquals("two, two, then the one left", listOf(2, 2, 1), game.requested)
    }

    @Test
    fun `fewer than five snippets in all make a shorter round`() {
        val vm = round(puzzles = 2, perPuzzle = 1).first.started()

        assertEquals(2, vm.question.total)
    }

    @Test
    fun `no puzzle with the technique is the empty state`() {
        assertEquals(RoundState.Empty, round(puzzles = 0).first.started().state.value)
        assertEquals(RoundState.Empty, round(puzzles = 4, perPuzzle = 0).first.started().state.value)
    }

    @Test
    fun `the round ends in a summary of the answers that were right`() {
        val vm = round(puzzles = 1, perPuzzle = 2).first.started()

        vm.verdict(TrainerVerdict.Right)
        vm.next()
        vm.verdict(TrainerVerdict.Wrong)
        assertTrue(vm.question.isLast)
        vm.next()

        assertEquals(RoundState.Summary(right = 1, total = 2), vm.state.value)
    }

    @Test
    fun `starting the same attempt again keeps the round and its answers`() {
        val vm = round(puzzles = 3).first.started()
        vm.verdict(TrainerVerdict.Right)
        val before = vm.state.value

        vm.start(1)

        assertEquals(before, vm.state.value)
    }

    @Test
    fun `a new attempt builds a new round from the first snippet`() {
        val vm = round(puzzles = 3).first.started()
        vm.verdict(TrainerVerdict.Right)
        vm.next()

        vm.start(2)

        assertEquals(0, vm.question.index)
        assertNull(vm.question.answer)
        assertEquals(0, vm.question.right)
    }

    @Test
    fun `a fresh view model after the process was killed starts at the first snippet and keeps the stored count`() {
        val before = round(puzzles = 3).first.started()
        before.verdict(TrainerVerdict.Right)
        before.next()
        before.verdict(TrainerVerdict.Right)

        val after = round(puzzles = 3).first.started()

        assertEquals(0, after.question.index)
        assertNull(after.question.answer)
        assertEquals("the count outlives the round", 2, stored()?.inRow)
    }
}
