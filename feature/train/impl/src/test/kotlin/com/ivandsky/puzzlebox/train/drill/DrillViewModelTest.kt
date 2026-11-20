package com.ivandsky.puzzlebox.train.drill

import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.FakeTrainerArchive
import com.ivandsky.puzzlebox.train.FakeTrainerGame
import com.ivandsky.puzzlebox.train.InlineDispatcherProvider
import com.ivandsky.puzzlebox.train.techniques
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DrillViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val queens = GameId("queens")
    private val technique = TechniqueId("line-lock")
    private val archive = FakeTrainerArchive()
    private val ui = FakeGamePlayUi(queens)

    private fun drill(vararg candidates: DrillCandidate): DrillViewModel {
        val game = FakeTrainerGame(queens, techniques("line-lock"), drillSources = listOf(DrillSource { candidates.toList() }))
        return DrillViewModel(game, technique, GamePlayRegistry(listOf(ui)), TestGames.registry(listOf(queens)), archive, InlineDispatcherProvider)
    }

    private fun archived(number: Int, steps: Int = 3) = DrillCandidate.Archive(number, steps)

    private fun bundled(id: String, steps: Int = 3) = DrillCandidate.Bundled(id, TestGames.puzzle(queens, 99), steps)

    private val DrillViewModel.playing: DrillState.Playing get() = state.value as DrillState.Playing

    private val FakeGamePlayController.alive: Boolean get() = env.scope.coroutineContext[Job]!!.isActive

    private val FakeGamePlayController.key: PuzzleKey get() = adopted.single().second.key

    /**
     * Catches a drill that fetches a puzzle it already holds: an archive puzzle on the device opens through the cache
     * with no fetch, plays under the throwaway `trainer-<game>-<n>` key and not under a daily or archive key, and one that
     * is not on the device is never asked for at the start.
     */
    @Test
    fun `an archive puzzle on the device opens without a fetch under the throwaway key`() {
        archive.cached += 9
        val vm = drill(archived(7, steps = 5), archived(9, steps = 3))

        vm.start(1)

        assertTrue(vm.state.value is DrillState.Playing)
        assertEquals(emptyList<Int>(), archive.fetches)
        assertEquals(listOf(9), archive.loads)
        val controller = ui.controllers.single()
        assertEquals(PuzzleKey.Custom("trainer-queens-9"), controller.key)
        assertEquals(TestGames.puzzle(queens, 9).payload.toList(), controller.adopted.single().first.payload.toList())
    }

    /** Catches a bundled puzzle that is loaded through the archive, which has no number for it, or keyed by an archive number. */
    @Test
    fun `a bundled puzzle opens with no load under its own id`() {
        val vm = drill(bundled("b1"))

        vm.start(1)

        assertTrue(vm.state.value is DrillState.Playing)
        assertEquals(emptyList<Int>(), archive.loads)
        assertEquals(PuzzleKey.Custom("trainer-queens-b1"), ui.controllers.single().key)
    }

    /** Catches candidates taken in the order the sources listed them, and a puzzle named twice that is offered twice. */
    @Test
    fun `candidates are tried the most target steps first and each puzzle once`() {
        archive.cached += listOf(3, 8)
        val vm = drill(archived(3, steps = 2), archived(8, steps = 6), archived(8, steps = 6))

        vm.start(1)

        assertEquals(listOf(8), archive.loads)
        assertEquals(PuzzleKey.Custom("trainer-queens-8"), ui.controllers.single().key)
    }

    /** Catches a drill that repeats the puzzle the round was just cut from while another is on the device, or one that refuses to open when only that puzzle is. */
    @Test
    fun `a puzzle the round used is avoided only while another is on the device`() {
        archive.cached += listOf(3, 8)
        drill(archived(8, steps = 6), archived(3, steps = 2)).start(1, avoid = setOf(8))
        assertEquals(listOf(3), archive.loads)

        archive.loads.clear()
        archive.cached -= 3
        drill(archived(8, steps = 6), archived(3, steps = 2)).start(1, avoid = setOf(8))
        assertEquals(listOf(8), archive.loads)
    }

    /** Catches a drill that opens on nothing, or that fetches at the start instead of waiting for the tap. */
    @Test
    fun `with nothing on the device it asks for a connection and one tap fetches one puzzle`() {
        val vm = drill(archived(7, steps = 5), archived(9, steps = 3))

        vm.start(1)
        assertEquals(DrillState.NeedsConnection, vm.state.value)
        assertEquals(emptyList<Int>(), archive.loads)

        vm.tryAgain()

        assertTrue(vm.state.value is DrillState.Playing)
        assertEquals(listOf(7), archive.fetches)
        assertEquals(PuzzleKey.Custom("trainer-queens-7"), ui.controllers.single().key)
    }

    /** Catches a second tap while the first fetch is out that starts a second fetch or a second board. */
    @Test
    fun `a second tap while loading fetches nothing more`() {
        archive.gate = CompletableDeferred()
        val vm = drill(archived(7))
        vm.start(1)

        vm.tryAgain()
        vm.tryAgain()
        assertEquals(DrillState.Loading, vm.state.value)
        archive.gate!!.complete(Unit)

        assertEquals(listOf(7), archive.loads)
        assertEquals(1, ui.controllers.size)
        assertTrue(vm.state.value is DrillState.Playing)
    }

    /** Catches a busy or offline answer that is held against the puzzle, so the next tap cannot try it again. */
    @Test
    fun `a busy fetch asks the same puzzle on the next tap`() {
        archive.script(7, ArchiveLoad.CantLoad(7, ArchiveLoad.CantLoad.Reason.Busy), ArchiveLoad.Loaded(7, TestGames.puzzle(queens, 7)))
        val vm = drill(archived(7), archived(9))
        vm.start(1)

        vm.tryAgain()
        assertEquals(DrillState.NeedsConnection, vm.state.value)
        vm.tryAgain()

        assertEquals(listOf(7, 7), archive.loads)
        assertTrue(vm.state.value is DrillState.Playing)
    }

    /** Catches a gone puzzle that every tap asks for again, which strands the player on the same dead number. */
    @Test
    fun `a gone puzzle is not asked for again while another is named`() {
        archive.script(7, ArchiveLoad.CantLoad(7, ArchiveLoad.CantLoad.Reason.Gone))
        val vm = drill(archived(7, steps = 5), archived(9, steps = 3))
        vm.start(1)

        vm.tryAgain()
        vm.tryAgain()

        assertEquals(listOf(7, 9), archive.loads)
        assertEquals(PuzzleKey.Custom("trainer-queens-9"), ui.controllers.single().key)
    }

    /** Catches a puzzle that fails to open and leaves the player on a spinner or an error with no way on. */
    @Test
    fun `a puzzle that fails to open offers one already on the device`() {
        archive.cached += listOf(5, 9)
        archive.script(5, ArchiveLoad.Mismatch(5))
        val vm = drill(archived(5, steps = 5), archived(9, steps = 3))

        vm.start(1)
        assertEquals(DrillState.CantLoad(archived(9, steps = 3)), vm.state.value)

        vm.tryAnother()

        assertTrue(vm.state.value is DrillState.Playing)
        assertEquals(PuzzleKey.Custom("trainer-queens-9"), ui.controllers.single().key)
    }

    /** Catches a refused puzzle that is shown anyway, that leaks the controller it was refused on, or that is offered again by its own can't-load tap. */
    @Test
    fun `a puzzle the game refuses is a failed open and leaves no live board behind`() {
        ui.rejectAdopt = "not playable"
        archive.cached += listOf(5, 9)
        val vm = drill(archived(5, steps = 5), archived(9, steps = 3))

        vm.start(1)
        assertEquals(DrillState.CantLoad(archived(9, steps = 3)), vm.state.value)
        vm.tryAnother()

        assertEquals(DrillState.NeedsConnection, vm.state.value)
        assertEquals(2, ui.controllers.size)
        assertTrue(ui.controllers.none { it.alive })
    }

    /** Catches a technique with no puzzle that spins or asks for a connection no fetch can answer. */
    @Test
    fun `a technique that names no puzzle is empty and a tap changes nothing`() {
        val vm = drill()

        vm.start(1)
        vm.tryAgain()

        assertEquals(DrillState.Empty, vm.state.value)
        assertEquals(emptyList<Int>(), archive.loads)
    }

    /** Catches a screen turn that rebuilds the board and loses the player's moves, and a new opening that keeps the old board. */
    @Test
    fun `the same attempt keeps the board and a new one builds another`() {
        archive.cached += 9
        val vm = drill(archived(9))
        vm.start(1)
        val first = vm.playing

        vm.start(1)
        assertSame(first, vm.playing)
        assertEquals(1, ui.controllers.size)

        vm.start(2)
        assertNotSame(first.controller, vm.playing.controller)
        assertEquals(2, ui.controllers.size)
        assertFalse(ui.controllers.first().alive)
    }

    /**
     * Catches a win that removes the board at once, before the solve sweep has played, and a solved page that waits
     * for nothing: a win only marks the board, and the sweep's report is what shows the page.
     */
    @Test
    fun `a win marks the board and the sweep's report shows the solved page`() {
        archive.cached += 9
        val vm = drill(archived(9))
        vm.start(1)
        val controller = ui.controllers.single()

        controller.win()

        assertSame(controller, vm.playing.controller)
        assertTrue(vm.playing.won)
        assertTrue(controller.alive)

        vm.solved()

        assertEquals(DrillState.Solved, vm.state.value)
        assertFalse(controller.alive)
    }

    /** Catches a solved page that shows for a board nobody won, such as one reported by a stale sweep. */
    @Test
    fun `a solved report without a win is ignored`() {
        archive.cached += 9
        val vm = drill(archived(9))
        vm.start(1)

        vm.solved()

        assertFalse(vm.playing.won)
    }

    /** Catches a back that leaves the board and its clock alive, and a reopened technique that resumes the old board. */
    @Test
    fun `leaving drops the board and the next start begins again`() {
        archive.cached += 9
        val vm = drill(archived(9))
        vm.start(1)
        val controller = ui.controllers.single()

        vm.leave()
        controller.win()

        assertEquals(DrillState.Loading, vm.state.value)
        assertFalse(controller.alive)

        vm.start(1)

        assertTrue(vm.state.value is DrillState.Playing)
        assertEquals(2, ui.controllers.size)
    }

    /** Catches a fetch that finishes after the player left and opens a board nobody asked for. */
    @Test
    fun `a fetch the player left before it finished opens nothing`() {
        archive.gate = CompletableDeferred()
        val vm = drill(archived(7))
        vm.start(1)
        vm.tryAgain()

        vm.leave()
        archive.gate!!.complete(Unit)

        assertEquals(DrillState.Loading, vm.state.value)
        assertEquals(emptyList<Any>(), ui.controllers)
    }
}
