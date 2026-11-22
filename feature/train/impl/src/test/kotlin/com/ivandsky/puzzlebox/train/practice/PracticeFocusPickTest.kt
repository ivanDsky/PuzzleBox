package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.named
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Which puzzle a focus board is cut from when the game says how many moves each trains: the first with two or more, else the usual order. */
class PracticeFocusPickTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val Puzzle.tag: Int get() = payload.last().toInt()

    /** A rig on [device] (newest first) whose game reports [moves], puzzle tag to trained moves; a tag not in it is unknown. */
    private fun rig(
        moves: Map<Int, Int>,
        device: List<Puzzle> = listOf(puzzle(1), puzzle(2), puzzle(3)),
        candidates: List<DrillCandidate> = named,
        attempt: Int? = 1,
    ): PracticeRig {
        val rig = PracticeRig(candidates, device = device, attempt = null)
        rig.game.trainedMovesOf = { puzzle, _ -> moves[puzzle.tag] }
        attempt?.let(rig.vm::start)
        return rig
    }

    /** Bug caught: board 1 cut from the newest daily though a later one trains several moves, so the board is one move and ends. */
    @Test
    fun `a board is cut from the first puzzle that trains two or more moves`() {
        val rig = rig(moves = mapOf(1 to 1, 2 to 3, 3 to 2))

        assertEquals(2, rig.board.tag)
    }

    /** Bug caught: two moves not counting as enough, so the preference asks for more than the ticket does. */
    @Test
    fun `two moves are enough`() {
        val rig = rig(moves = mapOf(1 to 1, 2 to 2))

        assertEquals(2, rig.board.tag)
    }

    /** Bug caught: a device with only one-move puzzles opening nothing, or an order other than the usual one. */
    @Test
    fun `with no puzzle of two or more moves the usual order stands`() {
        val rig = rig(moves = mapOf(1 to 1, 2 to 1, 3 to 1))

        assertEquals(1, rig.board.tag)
    }

    /** Bug caught: a game that does not report counts (Queens, Tango) changing order because an unknown was read as small or large. */
    @Test
    fun `a game that reports nothing keeps the usual order`() {
        val rig = rig(moves = emptyMap())

        assertEquals(1, rig.board.tag)
    }

    /** Bug caught: a puzzle with several moves that opens no board (the technique is never first there) stalling the pick instead of trying the next. */
    @Test
    fun `a long puzzle that opens no board is passed over for the next`() {
        val rig = rig(moves = mapOf(1 to 1, 2 to 4, 3 to 2), attempt = null)
        rig.game.silent += 2
        rig.vm.start(1)

        assertEquals(3, rig.board.tag)
    }

    /** Bug caught: the preference stopping at the on-device dailies, so a bundled drill of several moves is never reached when every daily is one move. */
    @Test
    fun `a bundled puzzle of two or more moves beats on-device dailies of one`() {
        val bundled = DrillCandidate.Bundled("b9", puzzle(9), targetSteps = 3)
        val rig = rig(moves = mapOf(1 to 1, 2 to 1, 9 to 2), candidates = listOf(bundled))

        assertEquals(9, rig.board.tag)
    }

    /** Bug caught: a bundled puzzle of one move winning over an on-device daily of the same count, losing the on-device-first order. */
    @Test
    fun `on the device first among puzzles that qualify equally`() {
        val bundled = DrillCandidate.Bundled("b9", puzzle(9), targetSteps = 3)
        val rig = rig(moves = mapOf(1 to 1, 2 to 2, 9 to 5), candidates = listOf(bundled))

        assertEquals(2, rig.board.tag)
    }

    /** Bug caught: the next board reopening a puzzle already played, or the preference lost after board 1 so board 2 is a one-move puzzle. */
    @Test
    fun `the next board is the next puzzle of two or more moves, then the usual order`() {
        val rig = rig(moves = mapOf(1 to 1, 2 to 3, 3 to 2))
        val tags = mutableListOf(rig.board.tag)

        repeat(2) {
            rig.finishBoard(main)
            tags += rig.board.tag
        }

        assertEquals(listOf(2, 3, 1), tags)
    }

    /** Bug caught: the review board (its own run, not a focus run) chosen by a focus run's move count. */
    @Test
    fun `the review board keeps the usual order`() {
        val rig = rig(moves = (4..8).associateWith { 3 }, device = (1..8).map(::puzzle))
        val tags = mutableListOf(rig.board.tag)

        repeat(4) {
            rig.finishBoard(main)
            tags += rig.board.tag
        }

        assertEquals(listOf(4, 5, 6, 7, 1), tags)
    }

    /** Bug caught: board 1 after the intro replaying the demo's puzzle because it is the one that trains the most moves. */
    @Test
    fun `board 1 after the intro is still another puzzle than the demo's`() {
        val rig = rig(moves = mapOf(1 to 1, 2 to 5, 3 to 2), attempt = null)
        rig.game.variantList = listOf(IntroVariant("One", "Rule one.", "Spot one.", "Do one."))
        rig.game.demoFor = { puzzle, _, _ -> if (puzzle.tag == 2) StandInIntroDemo(0, listOf("a", "b")) else null }
        rig.vm.start(1)

        rig.vm.startPractice()

        assertEquals(3, rig.board.tag)
    }
}
