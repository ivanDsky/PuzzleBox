package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.LINE_LOCK
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.QUEENS
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.named
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The board the view model hands out: what it is cut from, what its trained turns do to the stored progress, and how the next board is found. */
class PracticeBoardTest {

    @get:Rule
    val main = MainDispatcherRule()

    private suspend fun PracticeRig.seedRow(row: Int) {
        progress.update(QUEENS, LINE_LOCK) { TechniqueProgress(Mark.Practicing, row, 1) }
    }

    private val PracticeRig.alive: Boolean get() = board.scope.coroutineContext[Job]!!.isActive

    /** Bug caught: a board cut from an older puzzle than the newest the device has, or for another technique. */
    @Test
    fun `the board is cut from the newest puzzle on the device for the technique`() {
        val rig = PracticeRig(named, device = listOf(puzzle(7), puzzle(8)))

        assertEquals(7, rig.board.tag)
        assertEquals(LINE_LOCK, rig.board.technique)
    }

    /** Bug caught: a trained Right that does not raise the in-a-row count, or leaves the technique New. */
    @Test
    fun `a Right turn raises the in-a-row count and marks Practicing`() {
        val rig = PracticeRig(named, device = listOf(puzzle(1)))

        rig.board.turn(TrainerVerdict.Right)

        assertEquals(Mark.Practicing, rig.stored?.mark)
        assertEquals(1, rig.stored?.inRow)
        assertEquals(1, rig.vm.progress.value.inRow)
    }

    /** Bug caught: a Wrong turn that keeps the count, or that leaves a technique the player got wrong New. */
    @Test
    fun `a Wrong turn resets the in-a-row count`() = runTest {
        val rig = PracticeRig(named, device = listOf(puzzle(1)))
        rig.seedRow(3)

        rig.board.turn(TrainerVerdict.Wrong)

        assertEquals(0, rig.stored?.inRow)
        assertEquals(Mark.Practicing, rig.stored?.mark)
        assertEquals(0, rig.vm.progress.value.inRow)
    }

    /** Bug caught: Show me that keeps a row it is part of, or that marks a technique the player has not tried Practicing. */
    @Test
    fun `Show me resets the count and leaves the mark`() = runTest {
        val seeded = PracticeRig(named, device = listOf(puzzle(1)))
        seeded.seedRow(3)
        seeded.board.turn(TrainerVerdict.ShowMe)
        assertEquals(TechniqueProgress(Mark.Practicing, 0, seeded.stored!!.answeredAtMs), seeded.stored)

        val fresh = PracticeRig(named, device = listOf(puzzle(1)))
        fresh.board.turn(TrainerVerdict.ShowMe)
        assertEquals(Mark.New, fresh.stored?.mark)
    }

    /** Bug caught: five Rights in a row that do not master the technique, or a Wrong between them that does not break the row. */
    @Test
    fun `five Rights in a row master it and a Wrong breaks the row`() {
        val broken = PracticeRig(named, device = listOf(puzzle(1)))
        repeat(4) { broken.board.turn(TrainerVerdict.Right) }
        broken.board.turn(TrainerVerdict.Wrong)
        broken.board.turn(TrainerVerdict.Right)
        assertEquals(1, broken.stored?.inRow)
        assertEquals(Mark.Practicing, broken.stored?.mark)

        val straight = PracticeRig(named, device = listOf(puzzle(1)))
        repeat(5) { straight.board.turn(TrainerVerdict.Right) }
        assertEquals(Mark.Mastered, straight.stored?.mark)
    }

    /** Bug caught (A6): a count kept in the board, so the next board and a fresh view model start it again from nothing. */
    @Test
    fun `the count outlives the board and the view model`() = runTest {
        val rig = PracticeRig(named, device = listOf(puzzle(1), puzzle(2)))
        rig.board.turn(TrainerVerdict.Right)
        rig.board.turn(TrainerVerdict.Right)

        rig.vm.nextBoard()
        assertEquals(2, rig.vm.progress.value.inRow)

        val fresh = PracticeRig(named, device = listOf(puzzle(1), puzzle(2)), progress = rig.progress)
        assertEquals("a restore starts a board and keeps the stored count", 2, fresh.vm.progress.value.inRow)
        assertEquals(1, fresh.board.tag)
    }

    /** Bug caught: a Wrong that lands before the Right it followed, so the row survives a mistake. */
    @Test
    fun `turns are recorded in the order they came`() {
        val rig = PracticeRig(named, device = listOf(puzzle(1)))

        rig.board.turn(TrainerVerdict.Right)
        rig.board.turn(TrainerVerdict.Wrong)
        rig.board.turn(TrainerVerdict.Right)

        assertEquals(1, rig.stored?.inRow)
    }

    /** Bug caught: the same attempt again (the screen turned) replacing the board mid-play, or a new attempt leaving the old board alive. */
    @Test
    fun `the same attempt keeps the board and a new one replaces it`() {
        val rig = PracticeRig(named, device = listOf(puzzle(1)))
        val first = rig.board
        assertTrue(rig.alive)

        rig.vm.start(1)
        assertSame(first, rig.board)

        rig.vm.start(2)
        assertNotSame(first, rig.board)
        assertFalse("the dropped board's scope is no longer running", first.scope.coroutineContext[Job]!!.isActive)
        assertTrue(rig.alive)
    }

    /** Bug caught: Next board repeating the puzzle just played, or stopping when the device's puzzles are used up. */
    @Test
    fun `Next board opens another puzzle on the device and cycles when none are left`() {
        val rig = PracticeRig(emptyList(), device = listOf(puzzle(1), puzzle(2)))
        val tags = mutableListOf(rig.board.tag)

        repeat(3) {
            rig.vm.nextBoard()
            tags += rig.board.tag
        }

        assertEquals(listOf(1, 2, 1, 2), tags)
    }

    /** Bug caught: Next board dropping the board it replaces without ending its scope. */
    @Test
    fun `Next board ends the board it replaces`() {
        val rig = PracticeRig(emptyList(), device = listOf(puzzle(1), puzzle(2)))
        val first = rig.board

        rig.vm.nextBoard()

        assertFalse(first.scope.coroutineContext[Job]!!.isActive)
    }

    /** Bug caught: Next board fetching before the device's own puzzles are used, or fetching more than one puzzle for one tap. */
    @Test
    fun `Next board falls back to one fetch when the device's puzzles are played`() {
        val rig = PracticeRig(named, device = listOf(puzzle(1)))

        rig.vm.nextBoard()

        assertEquals(listOf(20), rig.archive.loads)
        assertEquals(20, rig.board.tag)

        rig.vm.nextBoard()
        assertEquals("one fetch a tap, the played puzzles are not asked for again", listOf(20, 30), rig.archive.loads)
        assertEquals(30, rig.board.tag)
    }

    /** Bug caught: a fetch that fails after a solved board leaving the player on a dead page instead of the connection message. */
    @Test
    fun `a failed fetch after a board leaves the connection message and the progress alone`() {
        val rig = PracticeRig(named, device = listOf(puzzle(1)))
        rig.board.turn(TrainerVerdict.Right)
        rig.archive.script(20, ArchiveLoad.CantLoad(20, ArchiveLoad.CantLoad.Reason.Offline))

        rig.vm.nextBoard()

        assertEquals(PracticeState.NeedsConnection, rig.state)
        assertEquals(1, rig.stored?.inRow)
    }

    /** Bug caught: a puzzle that never has the technique first treated as a board, or ending the search for one. */
    @Test
    fun `a puzzle with no point for the technique is skipped`() {
        val rig = PracticeRig(named, device = listOf(puzzle(1), puzzle(2)), attempt = null)
        rig.game.silent += 1

        rig.vm.start(1)

        assertEquals(2, rig.board.tag)
        assertEquals(listOf(2), rig.game.boards.map { it.tag })
    }
}
