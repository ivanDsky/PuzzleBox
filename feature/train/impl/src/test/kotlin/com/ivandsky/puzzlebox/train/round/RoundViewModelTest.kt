package com.ivandsky.puzzlebox.train.round

import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerProgressRepository
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerPuzzleSource
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.FakeSnippet
import com.ivandsky.puzzlebox.train.FakeTrainerArchive
import com.ivandsky.puzzlebox.train.FakeTrainerGame
import com.ivandsky.puzzlebox.train.InlineDispatcherProvider
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import com.ivandsky.puzzlebox.train.techniques
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
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
        return RoundViewModel(game, technique, source, FakeTrainerArchive(), RecordAnswer(progress), InlineDispatcherProvider) to game
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

    // The load states: what a tap fetches, what it shows when nothing comes, and that no load is an answer.

    /** The view model with the archive numbers [candidates] named, [device] the puzzles on the device, and its fakes. */
    private class Rig(
        val vm: RoundViewModel,
        val game: FakeTrainerGame,
        val archive: FakeTrainerArchive,
        val device: MutableList<Puzzle>,
    ) {
        val state: RoundState get() = vm.state.value
    }

    private fun archiveRound(candidates: List<DrillCandidate>, device: List<Puzzle> = emptyList(), attempt: Int? = 1): Rig {
        val game = FakeTrainerGame(queens, techniques("line-lock"), drillSources = listOf(DrillSource { candidates }))
        val archive = FakeTrainerArchive()
        val onDevice = device.toMutableList()
        archive.onLoaded = { onDevice += it }
        val source = TrainerPuzzleSource { flow { onDevice.toList().forEach { emit(it) } } }
        val vm = RoundViewModel(game, technique, source, archive, RecordAnswer(progress), InlineDispatcherProvider)
        attempt?.let(vm::start)
        return Rig(vm, game, archive, onDevice)
    }

    /** Archive numbers 10, 20 and 30 with the steps that order them 20, 30, 10. */
    private val named = listOf(
        DrillCandidate.Archive(number = 10, targetSteps = 5),
        DrillCandidate.Archive(number = 20, targetSteps = 9),
        DrillCandidate.Archive(number = 30, targetSteps = 7),
    )

    private fun failure(number: Int, reason: ArchiveLoad.CantLoad.Reason) = ArchiveLoad.CantLoad(number, reason)

    /**
     * Bug caught: a round that fetches (or offers a fetch only after fetching) before the player asks, or shows
     * the empty message to a player who only needs to connect.
     */
    @Test
    fun `nothing on the device with archive numbers named asks for a connection and fetches nothing`() {
        val rig = archiveRound(named)

        assertEquals(RoundState.NeedsConnection, rig.state)
        assertEquals("no prefetch", emptyList<Int>(), rig.archive.loads)
    }

    /**
     * Bug caught: Empty shown where a fetch could help, or NeedsConnection where no number is named; a bundled
     * candidate is not an archive number to load.
     */
    @Test
    fun `Empty only when the technique names no archive number at all`() {
        val bundled = DrillCandidate.Bundled("b1", TestGames.puzzle(queens, 1), targetSteps = 4)

        assertEquals(RoundState.Empty, archiveRound(emptyList()).state)
        assertEquals(RoundState.Empty, archiveRound(listOf(bundled)).state)
        assertEquals(RoundState.NeedsConnection, archiveRound(named).state)
    }

    /**
     * Bug caught: the offer of a fetch shown to a player whose device already has puzzles with the technique, or a
     * fetch before those are used.
     */
    @Test
    fun `a puzzle on the device starts the round with no fetch`() {
        val rig = archiveRound(named, device = listOf(TestGames.puzzle(queens, 1)))

        assertEquals(2, (rig.state as RoundState.Question).total)
        assertEquals(emptyList<Int>(), rig.archive.loads)
    }

    /**
     * Bug caught: a tap that loads a number other than the one with the most steps, loads more than one, or does not
     * start the round from the loaded puzzle.
     */
    @Test
    fun `the tap loads the most-steps number once and the round is cut from it`() {
        val rig = archiveRound(named)

        rig.vm.tryAgain()

        assertEquals(listOf(20), rig.archive.loads)
        val question = rig.state as RoundState.Question
        assertEquals(listOf("20-0", "20-1"), question.snippets.map { (it as FakeSnippet).label })
        assertNull("a load is not an answer", stored())
    }

    /**
     * Bug caught: a number already on the device fetched again, so the tap spends a call to learn what the device
     * knows.
     */
    @Test
    fun `the tap skips a number that is already on the device`() {
        val rig = archiveRound(named)
        rig.archive.cached += 20

        rig.vm.tryAgain()

        assertEquals(listOf(30), rig.archive.loads)
    }

    /**
     * Bug caught: the loaded puzzle cut twice (it is in the cache by the time the device is read), or the device's
     * own puzzles left out of the round once a puzzle was fetched.
     */
    @Test
    fun `the round goes on through the device and does not cut the loaded puzzle twice`() {
        val rig = archiveRound(named, device = listOf(TestGames.puzzle(queens, 1)), attempt = null)
        rig.game.silent += 1
        rig.vm.start(1)
        assertEquals("a device puzzle with no step is not a round", RoundState.NeedsConnection, rig.state)
        rig.game.silent -= 1
        rig.game.requested.clear()

        rig.vm.tryAgain()

        assertEquals("the loaded puzzle, then the one on the device, the loaded one not again", listOf(2, 2), rig.game.requested)
        assertEquals(4, (rig.state as RoundState.Question).total)
    }

    /**
     * Bug caught: a double tap that starts two loads (two fetches for one wish), or a tap that is lost because the
     * first one is still going.
     */
    @Test
    fun `a second tap while the first is loading fetches nothing more`() {
        val rig = archiveRound(named)
        val gate = CompletableDeferred<Unit>()
        rig.archive.gate = gate

        rig.vm.tryAgain()
        rig.vm.tryAgain()
        rig.vm.tryAnother()

        assertEquals(RoundState.Loading, rig.state)
        assertEquals(listOf(20), rig.archive.loads)
        gate.complete(Unit)
        assertTrue(rig.state is RoundState.Question)
        assertEquals(listOf(20), rig.archive.loads)
    }

    /**
     * Bug caught: a tap acting in a state it does not belong to: a fetch from a running round, or a load of a number
     * that nothing offered.
     */
    @Test
    fun `each action is ignored outside its own state`() {
        val rig = archiveRound(named, device = listOf(TestGames.puzzle(queens, 1)))
        val question = rig.state
        rig.vm.tryAgain()
        rig.vm.tryAnother()
        assertEquals(question, rig.state)

        val connect = archiveRound(named)
        connect.vm.tryAnother()
        assertEquals(RoundState.NeedsConnection, connect.state)
        assertEquals(emptyList<Int>(), rig.archive.loads + connect.archive.loads)
    }

    /**
     * Bug caught (A14): a failed or mismatched load that counts as an answer, so a connection problem breaks a
     * player's in-a-row count or marks a technique Practicing.
     */
    @Test
    fun `a failed or mismatched load never reaches the progress store`() = runTest {
        seedRow(3)
        val outcomes = listOf(
            ArchiveLoad.Mismatch(20),
            failure(20, ArchiveLoad.CantLoad.Reason.Busy),
            failure(20, ArchiveLoad.CantLoad.Reason.Offline),
            failure(20, ArchiveLoad.CantLoad.Reason.Gone),
            failure(20, ArchiveLoad.CantLoad.Reason.Untrusted),
        )
        for (outcome in outcomes) {
            val rig = archiveRound(named)
            rig.archive.script(20, outcome)
            rig.vm.tryAgain()
            assertEquals(outcome.toString(), RoundState.NeedsConnection, rig.state)
        }

        assertEquals(TechniqueProgress(Mark.Practicing, 3, 1), stored())
    }

    /**
     * Bug caught: a failure that strands the player on the connection message while a puzzle is on the device, or
     * an alternative that is not loaded offline by its own tap.
     */
    @Test
    fun `a failed load offers a puzzle on the device and its tap loads that number`() {
        val rig = archiveRound(named)
        rig.archive.cached += 10
        rig.archive.script(20, failure(20, ArchiveLoad.CantLoad.Reason.Offline))

        rig.vm.tryAgain()

        assertEquals(RoundState.CantLoad(alternative = 10), rig.state)
        rig.vm.tryAnother()
        assertEquals(listOf(20, 10), rig.archive.loads)
        assertEquals(listOf("10-0", "10-1"), (rig.state as RoundState.Question).snippets.map { (it as FakeSnippet).label })
        assertNull(stored())
    }

    /**
     * Bug caught: a mismatch handled differently from a failed load: the player must see the same can't-load
     * message and the same way out.
     */
    @Test
    fun `a mismatch behaves as a failed load for the player`() {
        val withAlternative = archiveRound(named)
        withAlternative.archive.cached += 10
        withAlternative.archive.script(20, ArchiveLoad.Mismatch(20))
        withAlternative.vm.tryAgain()
        assertEquals(RoundState.CantLoad(alternative = 10), withAlternative.state)

        val alone = archiveRound(named)
        alone.archive.script(20, ArchiveLoad.Mismatch(20))
        alone.vm.tryAgain()
        assertEquals(RoundState.NeedsConnection, alone.state)
    }

    /**
     * Bug caught: an alternative that is itself a failure (it no longer loads) offered again and again, or a second
     * failure that strands the player.
     */
    @Test
    fun `an alternative that fails is not offered again`() {
        val rig = archiveRound(named)
        rig.archive.cached += setOf(10, 30)
        rig.archive.script(20, failure(20, ArchiveLoad.CantLoad.Reason.Offline))
        rig.archive.script(30, ArchiveLoad.Mismatch(30))

        rig.vm.tryAgain()
        assertEquals(RoundState.CantLoad(alternative = 30), rig.state)
        rig.vm.tryAnother()

        assertEquals(RoundState.CantLoad(alternative = 10), rig.state)
    }

    /**
     * Bug caught: a busy or offline load that moves on (the number is fine and would work on a retry) or a gone,
     * untrusted or mismatched one that is asked for again (the player taps the same dead end).
     */
    @Test
    fun `busy and offline retry the same number, the rest move on to the next`() {
        val retried = listOf(ArchiveLoad.CantLoad.Reason.Busy, ArchiveLoad.CantLoad.Reason.Offline)
        for (reason in retried) {
            val rig = archiveRound(named)
            rig.archive.script(20, failure(20, reason), ArchiveLoad.Loaded(20, TestGames.puzzle(queens, 20)))
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
            val rig = archiveRound(named)
            rig.archive.script(20, outcome)
            rig.vm.tryAgain()
            rig.vm.tryAgain()
            assertEquals(outcome.toString(), listOf(20, 30), rig.archive.loads)
        }
    }

    /**
     * Bug caught: a puzzle that loads but gives no snippet offered as an alternative or fetched again, so the player
     * loops on a puzzle the round cannot use.
     */
    @Test
    fun `a loaded puzzle with no snippet counts as a failed load`() {
        val rig = archiveRound(named)
        rig.game.silent += 20

        rig.vm.tryAgain()
        assertEquals("the puzzle is on the device but is no alternative", RoundState.NeedsConnection, rig.state)
        rig.vm.tryAgain()

        assertEquals(listOf(20, 30), rig.archive.loads)
    }

    /**
     * Bug caught: a player stranded when every named number has failed once: the tap must try again from the top
     * rather than do nothing, and Empty is only for a technique that names nothing.
     */
    @Test
    fun `when every named number has failed the tap begins again`() {
        val rig = archiveRound(listOf(DrillCandidate.Archive(20, 9)))
        rig.archive.script(20, failure(20, ArchiveLoad.CantLoad.Reason.Gone), ArchiveLoad.Loaded(20, TestGames.puzzle(queens, 20)))

        rig.vm.tryAgain()
        assertEquals(RoundState.NeedsConnection, rig.state)
        rig.vm.tryAgain()

        assertEquals(listOf(20, 20), rig.archive.loads)
        assertTrue(rig.state is RoundState.Question)
    }

    /**
     * Bug caught: a tap that loads a number already on the device (or loops) when there is nothing left to fetch.
     */
    @Test
    fun `nothing left to fetch is the empty state with no load`() {
        val rig = archiveRound(listOf(DrillCandidate.Archive(20, 9)))
        rig.archive.cached += 20

        rig.vm.tryAgain()

        assertEquals(RoundState.Empty, rig.state)
        assertEquals(emptyList<Int>(), rig.archive.loads)
    }

    /**
     * Bug caught: a load that finishes after the player opened the technique again and overwrites the new round.
     */
    @Test
    fun `a new attempt drops a load in flight`() {
        val rig = archiveRound(named)
        val gate = CompletableDeferred<Unit>()
        rig.archive.gate = gate
        rig.vm.tryAgain()

        rig.vm.start(2)
        gate.complete(Unit)

        assertEquals(RoundState.NeedsConnection, rig.state)
    }
}
