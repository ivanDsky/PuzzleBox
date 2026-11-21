package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.LINE_LOCK
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.NAKED_SINGLE
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.QUEENS
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The chapter a technique's practice is: four focus boards and a review board, a result page after them, and the next
 * technique only from that page's button. Every wait is virtual time on the main dispatcher.
 */
class PracticeChapterTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val pair = TechniqueId("pair")
    private val device = listOf(puzzle(1), puzzle(2))

    private fun rig(catalog: List<String>, opens: TechniqueId) =
        PracticeRig(emptyList(), device = device, catalog = catalog, opens = opens)

    private fun advance(ms: Long) = main.testDispatcher.scheduler.apply {
        advanceTimeBy(ms)
        runCurrent()
    }

    /** Bug caught: a finished board that opens the next one at once, so the player never sees what finished it. */
    @Test
    fun `a finished board waits out its hold before the next opens`() {
        val rig = rig(listOf("naked-single", "line-lock"), LINE_LOCK)

        rig.board.say(PracticeStatus.Done(null))
        advance(BoardEndHoldMillis - 1)
        assertEquals(1, rig.game.boards.size)
        assertEquals(1, rig.vm.chapter.value.board)

        advance(1)
        assertEquals(2, rig.game.boards.size)
        assertEquals(2, rig.vm.chapter.value.board)
    }

    /** Bug caught: a chapter with no review board, or a review that comes before the fourth focus board is done. */
    @Test
    fun `four focus boards and then a review make the chapter of a later technique`() {
        val rig = rig(listOf("naked-single", "line-lock"), LINE_LOCK)

        repeat(4) { rig.finishBoard(main) }

        assertEquals(List(4) { BoardKind.Focus } + BoardKind.Review, rig.game.boards.map { it.kind })
        assertEquals(5, rig.vm.chapter.value.board)
        assertEquals(5, rig.vm.chapter.value.boards)
    }

    /** Bug caught: the next technique opening by itself after the chapter, before the player has seen its result. */
    @Test
    fun `the first technique has four focus boards and its next technique opens from the result page only`() {
        val rig = rig(listOf("naked-single", "line-lock"), NAKED_SINGLE)
        assertEquals(4, rig.vm.chapter.value.boards)

        repeat(4) { rig.finishBoard(main) }
        assertEquals(List(4) { BoardKind.Focus }, rig.game.boards.map { it.kind })
        assertEquals(NAKED_SINGLE, (rig.state as PracticeState.Result).result.technique.id)
        assertEquals(LINE_LOCK, (rig.state as PracticeState.Result).result.next?.id)
        advance(BoardEndHoldMillis * 3)
        assertEquals("nothing opens on a timer", 4, rig.game.boards.size)

        rig.vm.nextChapter()

        assertEquals(List(5) { BoardKind.Focus }, rig.game.boards.map { it.kind })
        assertEquals(listOf(NAKED_SINGLE, NAKED_SINGLE, NAKED_SINGLE, NAKED_SINGLE, LINE_LOCK), rig.game.boards.map { it.technique })
        assertEquals(LINE_LOCK, rig.vm.chapter.value.technique.id)
        assertEquals(1, rig.vm.chapter.value.board)
        assertEquals(5, rig.vm.chapter.value.boards)
    }

    /** Bug caught: the next technique opened on the old one's header or progress, or at some board but the first. */
    @Test
    fun `after a chapter the next technique opens at board one with its own header and progress`() {
        val rig = rig(listOf("naked-single", "line-lock", "pair"), LINE_LOCK)

        repeat(5) { rig.finishBoard(main) }
        rig.vm.nextChapter()

        assertEquals(pair, rig.board.technique)
        assertEquals(BoardKind.Focus, rig.board.kind)
        assertEquals(pair, rig.vm.chapter.value.technique.id)
        assertEquals("Pair", rig.vm.chapter.value.technique.name)
        assertEquals(1, rig.vm.chapter.value.board)

        rig.board.turn(TrainerVerdict.Right)
        assertEquals("the count the header shows is the new technique's", 1, rig.vm.progress.value.inRow)
        assertEquals(1, rig.progress.stored[QUEENS]?.get(pair)?.inRow)
    }

    /** Bug caught: a late Right recorded against the technique the view model has moved on to, not the one its board trained. */
    @Test
    fun `a turn is recorded under the technique its board trained`() {
        val rig = rig(listOf("naked-single", "line-lock", "pair"), LINE_LOCK)
        val last = run {
            repeat(4) { rig.finishBoard(main) }
            rig.board
        }
        last.say(PracticeStatus.Done(null))

        last.turn(TrainerVerdict.Right)
        advance(BoardEndHoldMillis)

        assertEquals(1, rig.progress.stored[QUEENS]?.get(LINE_LOCK)?.inRow)
        assertEquals(null, rig.progress.stored[QUEENS]?.get(pair))
    }

    /** Bug caught: the last board of the last technique opening one more board, or leaving a dead page. */
    @Test
    fun `the last board of the last technique opens a result page with no next technique`() {
        val rig = rig(listOf("naked-single", "line-lock"), LINE_LOCK)

        repeat(4) { rig.finishBoard(main) }
        assertTrue(rig.state is PracticeState.Practicing)
        rig.finishBoard(main)

        val result = (rig.state as PracticeState.Result).result
        assertEquals(null, result.next)
        assertEquals(5, rig.game.boards.size)
    }

    /** Bug caught: a catalog of one technique, with no review, running past its fourth board. */
    @Test
    fun `a catalog of one technique finishes after four boards`() {
        val rig = rig(listOf("line-lock"), LINE_LOCK)

        repeat(3) { rig.finishBoard(main) }
        assertTrue(rig.state is PracticeState.Practicing)
        rig.finishBoard(main)

        assertTrue(rig.state is PracticeState.Result)
    }

    /** Bug caught: the hold of a board that was left still opening the next board over the one the player started. */
    @Test
    fun `a new attempt during the hold starts the chapter over and the old hold opens nothing`() {
        val rig = rig(listOf("naked-single", "line-lock"), LINE_LOCK)
        repeat(2) { rig.finishBoard(main) }
        rig.board.say(PracticeStatus.Done(null))

        rig.vm.start(2)
        val fresh = rig.board
        advance(BoardEndHoldMillis * 2)

        assertEquals(1, rig.vm.chapter.value.board)
        assertEquals(LINE_LOCK, rig.vm.chapter.value.technique.id)
        assertTrue(rig.board === fresh)
    }
}
