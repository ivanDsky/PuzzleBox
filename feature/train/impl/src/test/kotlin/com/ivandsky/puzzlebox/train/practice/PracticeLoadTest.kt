package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.LINE_LOCK
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.QUEENS
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.named
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The load states: what a tap fetches, what it shows when nothing comes, and that no load is a turn. */
class PracticeLoadTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun failure(number: Int, reason: ArchiveLoad.CantLoad.Reason) = ArchiveLoad.CantLoad(number, reason)

    /**
     * Bug caught: a practice that fetches (or offers a fetch only after fetching) before the player asks, or shows the
     * empty message to a player who only needs to connect.
     */
    @Test
    fun `nothing on the device with archive numbers named asks for a connection and fetches nothing`() {
        val rig = PracticeRig(named)

        assertEquals(PracticeState.NeedsConnection, rig.state)
        assertEquals("no prefetch", emptyList<Int>(), rig.archive.loads)
    }

    /** Bug caught: Empty shown where a fetch could help, or NeedsConnection where no number is named. */
    @Test
    fun `Empty only when the technique names no archive number at all`() {
        assertEquals(PracticeState.Empty, PracticeRig(emptyList()).state)
        assertEquals(PracticeState.NeedsConnection, PracticeRig(named).state)
    }

    /**
     * Bug caught: a bundled puzzle that is loaded through the archive (it has no number), or one that needs a connection:
     * it opens at once, with no load and no fetch, after the puzzles the device already holds.
     */
    @Test
    fun `a bundled puzzle opens a board with no load and no fetch`() {
        val rig = PracticeRig(listOf(DrillCandidate.Bundled("b1", puzzle(7), targetSteps = 4)))

        assertEquals(7, rig.board.tag)
        assertEquals(emptyList<Int>(), rig.archive.loads)
        assertEquals(emptyList<Int>(), rig.archive.fetches)
    }

    /** Bug caught: a bundled puzzle ahead of the device's own, or bundled puzzles taken in the order the source lists them. */
    @Test
    fun `the device's puzzles come before the bundled ones, which come most steps first`() {
        val bundled = listOf(DrillCandidate.Bundled("b1", puzzle(7), targetSteps = 2), DrillCandidate.Bundled("b2", puzzle(8), targetSteps = 6))
        val withDevice = PracticeRig(bundled, device = listOf(puzzle(1)))
        val without = PracticeRig(bundled)

        assertEquals(1, withDevice.board.tag)
        assertEquals(8, without.board.tag)
    }

    /** Bug caught: a bundled puzzle with no point for the technique shown as a board, or one that strands the player on a blank page. */
    @Test
    fun `a bundled puzzle with no point for the technique is skipped`() {
        val bundled = listOf(DrillCandidate.Bundled("b1", puzzle(7), targetSteps = 6), DrillCandidate.Bundled("b2", puzzle(8), targetSteps = 2))
        val rig = PracticeRig(bundled, attempt = null)
        rig.game.silent += 7

        rig.vm.start(1)

        assertEquals(8, rig.board.tag)
    }

    /** Bug caught: a next board that replays a bundled puzzle while another is unplayed, or that has nowhere to go after the last. */
    @Test
    fun `the next board goes through the bundled puzzles and comes round again`() {
        val bundled = listOf(DrillCandidate.Bundled("b1", puzzle(7), targetSteps = 6), DrillCandidate.Bundled("b2", puzzle(8), targetSteps = 2))
        val rig = PracticeRig(bundled)

        assertEquals(7, rig.board.tag)
        rig.finishBoard(main)
        assertEquals(8, rig.board.tag)
        rig.finishBoard(main)
        assertEquals(7, rig.board.tag)
    }

    /** Bug caught: a puzzle on the device whose practice never has the technique first is shown as a board, or hides the fetch offer. */
    @Test
    fun `a device puzzle with no point for the technique is not a board`() {
        val rig = PracticeRig(named, device = listOf(puzzle(1)), attempt = null)
        rig.game.silent += 1

        rig.vm.start(1)

        assertEquals(PracticeState.NeedsConnection, rig.state)
    }

    /**
     * Bug caught: the offer of a fetch shown to a player whose device already has puzzles with the technique, or a
     * fetch before those are used.
     */
    @Test
    fun `a puzzle on the device starts a board with no fetch`() {
        val rig = PracticeRig(named, device = listOf(puzzle(1)))

        assertTrue(rig.state is PracticeState.Practicing)
        assertEquals(emptyList<Int>(), rig.archive.loads)
    }

    /** Bug caught: a tap that loads a number other than the one with the most steps, loads more than one, or does not open a board on the loaded puzzle. */
    @Test
    fun `the tap loads the most-steps number once and opens a board on it`() {
        val rig = PracticeRig(named)

        rig.vm.tryAgain()

        assertEquals(listOf(20), rig.archive.loads)
        assertEquals(20, rig.board.tag)
        assertNull("a load is not a turn", rig.stored)
    }

    /** Bug caught: a number already on the device fetched again, so the tap spends a call to learn what the device knows. */
    @Test
    fun `the tap skips a number that is already on the device`() {
        val rig = PracticeRig(named)
        rig.archive.cached += 20

        rig.vm.tryAgain()

        assertEquals(listOf(30), rig.archive.loads)
    }

    /** Bug caught: a double tap that starts two loads (two fetches for one wish), or a tap that is lost because the first one is still going. */
    @Test
    fun `a second tap while the first is loading fetches nothing more`() {
        val rig = PracticeRig(named)
        val gate = CompletableDeferred<Unit>()
        rig.archive.gate = gate

        rig.vm.tryAgain()
        rig.vm.tryAgain()
        rig.vm.tryAnother()

        assertEquals(PracticeState.Loading, rig.state)
        assertEquals(listOf(20), rig.archive.loads)
        gate.complete(Unit)
        assertTrue(rig.state is PracticeState.Practicing)
        assertEquals(listOf(20), rig.archive.loads)
    }

    /** Bug caught: a tap acting in a state it does not belong to: a fetch from a running board, or a load of a number that nothing offered. */
    @Test
    fun `each action is ignored outside its own state`() {
        val playing = PracticeRig(named, device = listOf(puzzle(1)))
        val board = playing.board
        playing.vm.tryAgain()
        playing.vm.tryAnother()
        assertTrue(playing.board === board)

        val connect = PracticeRig(named)
        connect.vm.tryAnother()
        assertEquals(PracticeState.NeedsConnection, connect.state)
        assertEquals(emptyList<Int>(), playing.archive.loads + connect.archive.loads)
    }

    /**
     * Bug caught (A14): a failed or mismatched load that counts as a turn, so a connection problem breaks a player's
     * in-a-row count or marks a technique Practicing.
     */
    @Test
    fun `a failed or mismatched load never reaches the progress store`() = runTest {
        val outcomes = listOf(
            ArchiveLoad.Mismatch(20),
            failure(20, ArchiveLoad.CantLoad.Reason.Busy),
            failure(20, ArchiveLoad.CantLoad.Reason.Offline),
            failure(20, ArchiveLoad.CantLoad.Reason.Gone),
            failure(20, ArchiveLoad.CantLoad.Reason.Untrusted),
        )
        for (outcome in outcomes) {
            val rig = PracticeRig(named)
            rig.progress.update(QUEENS, LINE_LOCK) { TechniqueProgress(Mark.Practicing, 3, 1) }
            rig.archive.script(20, outcome)
            rig.vm.tryAgain()
            assertEquals(outcome.toString(), PracticeState.NeedsConnection, rig.state)
            assertEquals(outcome.toString(), TechniqueProgress(Mark.Practicing, 3, 1), rig.stored)
        }
    }

    /** Bug caught: a failure that strands the player on the connection message while a puzzle is on the device, or an alternative that is not loaded offline by its own tap. */
    @Test
    fun `a failed load offers a puzzle on the device and its tap loads that number`() {
        val rig = PracticeRig(named)
        rig.archive.cached += 10
        rig.archive.script(20, failure(20, ArchiveLoad.CantLoad.Reason.Offline))

        rig.vm.tryAgain()

        assertEquals(PracticeState.CantLoad(alternative = 10), rig.state)
        rig.vm.tryAnother()
        assertEquals(listOf(20, 10), rig.archive.loads)
        assertEquals(10, rig.board.tag)
        assertNull(rig.stored)
    }

    /** Bug caught: a mismatch handled differently from a failed load: the player must see the same can't-load message and the same way out. */
    @Test
    fun `a mismatch behaves as a failed load for the player`() {
        val withAlternative = PracticeRig(named)
        withAlternative.archive.cached += 10
        withAlternative.archive.script(20, ArchiveLoad.Mismatch(20))
        withAlternative.vm.tryAgain()
        assertEquals(PracticeState.CantLoad(alternative = 10), withAlternative.state)

        val alone = PracticeRig(named)
        alone.archive.script(20, ArchiveLoad.Mismatch(20))
        alone.vm.tryAgain()
        assertEquals(PracticeState.NeedsConnection, alone.state)
    }

    /** Bug caught: an alternative that is itself a failure (it no longer loads) offered again and again, or a second failure that strands the player. */
    @Test
    fun `an alternative that fails is not offered again`() {
        val rig = PracticeRig(named)
        rig.archive.cached += setOf(10, 30)
        rig.archive.script(20, failure(20, ArchiveLoad.CantLoad.Reason.Offline))
        rig.archive.script(30, ArchiveLoad.Mismatch(30))

        rig.vm.tryAgain()
        assertEquals(PracticeState.CantLoad(alternative = 30), rig.state)
        rig.vm.tryAnother()

        assertEquals(PracticeState.CantLoad(alternative = 10), rig.state)
    }

    /**
     * Bug caught: a busy or offline load that moves on (the number is fine and would work on a retry) or a gone,
     * untrusted or mismatched one that is asked for again (the player taps the same dead end).
     */
    @Test
    fun `busy and offline retry the same number, the rest move on to the next`() {
        val retried = listOf(ArchiveLoad.CantLoad.Reason.Busy, ArchiveLoad.CantLoad.Reason.Offline)
        for (reason in retried) {
            val rig = PracticeRig(named)
            rig.archive.script(20, failure(20, reason), ArchiveLoad.Loaded(20, puzzle(20)))
            rig.vm.tryAgain()
            rig.vm.tryAgain()
            assertEquals(reason.name, listOf(20, 20), rig.archive.loads)
        }

        val moved = listOf(
            failure(20, ArchiveLoad.CantLoad.Reason.Gone),
            failure(20, ArchiveLoad.CantLoad.Reason.Untrusted),
            ArchiveLoad.Mismatch(20),
        )
        for (outcome in moved) {
            val rig = PracticeRig(named)
            rig.archive.script(20, outcome)
            rig.vm.tryAgain()
            rig.vm.tryAgain()
            assertEquals(outcome.toString(), listOf(20, 30), rig.archive.loads)
        }
    }

    /** Bug caught: a puzzle that loads but never has the technique first offered as an alternative or fetched again, so the player loops on a puzzle the board cannot use. */
    @Test
    fun `a loaded puzzle with no point for the technique counts as a failed load`() {
        val rig = PracticeRig(named)
        rig.game.silent += 20

        rig.vm.tryAgain()
        assertEquals("the puzzle is on the device but is no alternative", PracticeState.NeedsConnection, rig.state)
        rig.vm.tryAgain()

        assertEquals(listOf(20, 30), rig.archive.loads)
    }

    /** Bug caught: a player stranded when every named number has failed once: the tap must try again from the top rather than do nothing. */
    @Test
    fun `when every named number has failed the tap begins again`() {
        val rig = PracticeRig(listOf(DrillCandidate.Archive(20, 9)))
        rig.archive.script(20, failure(20, ArchiveLoad.CantLoad.Reason.Gone), ArchiveLoad.Loaded(20, TestGames.puzzle(QUEENS, 20)))

        rig.vm.tryAgain()
        assertEquals(PracticeState.NeedsConnection, rig.state)
        rig.vm.tryAgain()

        assertEquals(listOf(20, 20), rig.archive.loads)
        assertTrue(rig.state is PracticeState.Practicing)
    }

    /** Bug caught: a tap that loads a number already on the device (or loops) when there is nothing left to fetch. */
    @Test
    fun `nothing left to fetch is the empty state with no load`() {
        val rig = PracticeRig(listOf(DrillCandidate.Archive(20, 9)))
        rig.archive.cached += 20

        rig.vm.tryAgain()

        assertEquals(PracticeState.Empty, rig.state)
        assertEquals(emptyList<Int>(), rig.archive.loads)
    }

    /** Bug caught: a load that finishes after the player opened the technique again and overwrites the new state. */
    @Test
    fun `a new attempt drops a load in flight`() {
        val rig = PracticeRig(named)
        val gate = CompletableDeferred<Unit>()
        rig.archive.gate = gate
        rig.vm.tryAgain()

        rig.vm.start(2)
        gate.complete(Unit)

        assertEquals(PracticeState.NeedsConnection, rig.state)
    }
}
