package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerProgressRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.LINE_LOCK
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.QUEENS
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.named
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The technique intro before board 1 (spec §2.7): when it is found, what Start practice opens, the replay, and the one-time hint. */
class PracticeIntroTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val Puzzle.tag: Int get() = payload.last().toInt()

    private val variants = listOf(IntroVariant("One", "Rule one.", "Spot one.", "Do one."), IntroVariant("Two", "Rule two.", "Spot two.", "Do two."))

    /**
     * A rig whose game has [variants] and demos [on]: variant to the puzzle tag it is recorded on, and whose technique was
     * already answered when [answered]. Not started: the test starts it.
     */
    private fun rig(on: Map<Int, Int>, answered: Boolean = false): PracticeRig {
        val progress = FakeTrainerProgressRepository()
        if (answered) runBlocking { progress.update(QUEENS, LINE_LOCK) { TechniqueProgress(TechniqueProgress.Mark.Practicing, 1, 1L) } }
        val rig = PracticeRig(named, device = listOf(puzzle(1), puzzle(2), puzzle(3)), attempt = null, progress = progress)
        rig.game.variantList = variants
        rig.game.demoFor = { puzzle, _, variant -> if (on[variant] == puzzle.tag) StandInIntroDemo(variant, listOf("a", "b", "c")) else null }
        rig.vm.start(1)
        return rig
    }

    /** Bug caught: board 1 cut and played before the player has watched anything, or a technique with demos opening with no intro. */
    @Test
    fun `a technique with demos opens its intro and cuts no board`() {
        val rig = rig(on = mapOf(0 to 1, 1 to 2))

        val intro = (rig.state as PracticeState.Intro).intro
        assertEquals(listOf("One", "Two"), intro.entries.map { it.variant.title })
        assertEquals(emptyList<Any>(), rig.game.boards)
    }

    /** Bug caught: the intro forced again on every open of a technique the player has already practiced. */
    @Test
    fun `a technique already answered opens on a board with the intro kept for the chip`() {
        val rig = rig(on = mapOf(0 to 2), answered = true)

        assertEquals(1, rig.board.tag)
        assertEquals(listOf("One"), rig.vm.intros.intro.value?.entries?.map { it.variant.title })
        rig.vm.intros.show()
        assertTrue(rig.vm.intros.replaying.value)
    }

    /** Bug caught: the intro shown for a technique no game has a demo for, so a game with no demos has a dead first screen. */
    @Test
    fun `no demo means no intro and practice starts as before`() {
        val rig = rig(on = emptyMap())

        assertEquals(1, rig.board.tag)
        assertEquals(null, rig.vm.intros.intro.value)
    }

    /** Bug caught: a variant listed with nothing to watch, or the first puzzle taken from variant 0 when only variant 1 has a demo. */
    @Test
    fun `a variant with no demo is left out and board 1 is the first entry's puzzle`() {
        val rig = rig(on = mapOf(1 to 3))

        val intro = (rig.state as PracticeState.Intro).intro
        assertEquals(listOf("Two"), intro.entries.map { it.variant.title })
        assertEquals(3, intro.first.tag)
    }

    /** Bug caught: board 1 replaying the demo's own puzzle, so the first move asked for is the one just watched. */
    @Test
    fun `start practice opens board 1 on a puzzle other than the one the demo used`() {
        val rig = rig(on = mapOf(0 to 2, 1 to 3))

        rig.vm.startPractice()
        assertNotEquals(2, rig.board.tag)
        assertEquals(1, rig.game.boards.size)
        rig.finishBoard(main)

        assertNotEquals(2, rig.board.tag)
    }

    /** Bug caught: a device with only the demo's puzzle opening nothing for board 1. */
    @Test
    fun `start practice falls back to the demo's puzzle when it is the only one`() {
        val progress = FakeTrainerProgressRepository()
        val rig = PracticeRig(named, device = listOf(puzzle(2)), attempt = null, progress = progress)
        rig.game.variantList = variants
        rig.game.demoFor = { puzzle, _, variant -> if (variant == 0 && puzzle.tag == 2) StandInIntroDemo(variant, listOf("a", "b", "c")) else null }
        rig.vm.start(1)

        rig.vm.startPractice()

        assertEquals(2, rig.board.tag)
    }

    /** Bug caught: Start practice working from a board (a stray tap on a button that is not on screen) and replacing it. */
    @Test
    fun `start practice does nothing outside the opening intro`() {
        val rig = rig(on = emptyMap())
        val board = rig.board

        rig.vm.startPractice()

        assertSame(board, rig.board)
        assertEquals(1, rig.game.boards.size)
    }

    /** Bug caught: the replay rebuilding the board, which would lose the marks and the band the player left behind. */
    @Test
    fun `replaying the intro and closing it gives the same board with its status`() {
        val rig = rig(on = mapOf(0 to 1))
        rig.vm.startPractice()
        val board = rig.board
        val wrong = PracticeStatus.Wrong("Two queens would touch.")
        board.say(wrong)

        rig.vm.intros.show()
        assertTrue(rig.vm.intros.replaying.value)
        assertSame(board, rig.board)
        rig.vm.intros.close()

        assertFalse(rig.vm.intros.replaying.value)
        assertSame(board, rig.board)
        assertEquals(wrong, board.status.value)
        assertEquals(1, rig.game.boards.size)
    }

    /** Bug caught: the chip doing something on a technique with no intro. */
    @Test
    fun `showing the intro of a technique that has none is ignored`() {
        val rig = rig(on = emptyMap())

        rig.vm.intros.show()

        assertFalse(rig.vm.intros.replaying.value)
    }

    /** Bug caught: the hint shown before the first intro is left, shown again after the player answered it, or shown again on the next technique. */
    @Test
    fun `the hint comes up when the first intro is left and never again after it is answered`() {
        val rig = rig(on = mapOf(0 to 1))
        assertFalse(rig.hint.visible.value)

        rig.vm.startPractice()
        assertTrue(rig.hint.visible.value)

        rig.vm.intros.dismissHint()
        assertFalse(rig.hint.visible.value)
        rig.hint.introLeft()
        assertFalse(rig.hint.visible.value)
    }

    /** Bug caught: the chip leaving the bubble up, pointing at a chip the player has already found. */
    @Test
    fun `using the chip answers the hint`() {
        val rig = rig(on = mapOf(0 to 1))
        rig.vm.startPractice()

        rig.vm.intros.show()

        assertFalse(rig.hint.visible.value)
    }
}
