package com.ivandsky.puzzlebox.train.tab

import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerProgressRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.FakeTrainerGame
import com.ivandsky.puzzlebox.train.techniques
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val queens = GameId("queens")
    private val tango = GameId("tango")
    private val queensGame = FakeTrainerGame(queens, techniques("singles", "line-lock", "squeeze"))
    private val tangoGame = FakeTrainerGame(tango, techniques("pairs", "trios"))
    private val registry = TrainerRegistry(listOf(queensGame, tangoGame), listOf(queens, tango))
    private val progress = FakeTrainerProgressRepository()

    /** A view model with a collector held for the test, as the screen holds one. */
    private fun TestScope.model(
        registry: TrainerRegistry = this@TrainViewModelTest.registry,
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        handle: SavedStateHandle = SavedStateHandle(),
    ): TrainViewModel {
        val vm = TrainViewModel(registry, prefs, progress, handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        advanceUntilIdle()
        return vm
    }

    private val TrainViewModel.current: TrainState get() = state.value!!

    @Test
    fun `the first open shows the registry's first game with its techniques in catalog order, all New`() = runTest {
        val vm = model()

        assertEquals(listOf(queens, tango), vm.current.games)
        assertEquals(queens, vm.current.selected)
        assertEquals(listOf("singles", "line-lock", "squeeze"), vm.current.techniques.map { it.technique.id.value })
        assertEquals(listOf(TechniqueProgress.NEW), vm.current.techniques.map { it.progress }.distinct())
        assertNull(vm.current.open)
    }

    @Test
    fun `the last game the player picked is restored`() = runTest {
        val vm = model(prefs = FakeUserPreferencesRepository(lastTrainGame = tango))

        assertEquals(tango, vm.current.selected)
        assertEquals(listOf("pairs", "trios"), vm.current.techniques.map { it.technique.id.value })
    }

    @Test
    fun `a stored game the registry no longer has falls back to the registry's first`() = runTest {
        val vm = model(prefs = FakeUserPreferencesRepository(lastTrainGame = GameId("minisudoku")))

        assertEquals(queens, vm.current.selected)
    }

    @Test
    fun `marks follow the progress repository, and a technique with no record reads New`() = runTest {
        val vm = model()

        progress.update(queens, TechniqueId("line-lock")) { TechniqueProgress(Mark.Practicing, 3, 7) }
        advanceUntilIdle()

        val marks = vm.current.techniques.associate { it.technique.id.value to it.progress }
        assertEquals(TechniqueProgress(Mark.Practicing, 3, 7), marks["line-lock"])
        assertEquals(TechniqueProgress.NEW, marks["singles"])
    }

    @Test
    fun `another game's progress does not show on this game's list`() = runTest {
        val vm = model()

        progress.update(tango, TechniqueId("pairs")) { TechniqueProgress(Mark.Mastered, 5, 7) }
        advanceUntilIdle()

        assertEquals(listOf(TechniqueProgress.NEW), vm.current.techniques.map { it.progress }.distinct())
    }

    @Test
    fun `picking a game shows its techniques and stores the pick`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val vm = model(prefs = prefs)

        vm.selectGame(tango)
        advanceUntilIdle()

        assertEquals(tango, vm.current.selected)
        assertEquals(tango, prefs.lastTrainGame.first())
    }

    @Test
    fun `picking a game the registry does not have changes nothing`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val vm = model(prefs = prefs)

        vm.selectGame(GameId("minisudoku"))
        advanceUntilIdle()

        assertEquals(queens, vm.current.selected)
        assertNull(prefs.lastTrainGame.first())
    }

    @Test
    fun `opening a technique opens its round for the game that trains it`() = runTest {
        val vm = model()

        vm.openTechnique(queens, TechniqueId("line-lock"))

        val open = vm.current.open!!
        assertEquals(queens, open.game.gameId)
        assertEquals("Line-lock", open.technique.name)
    }

    @Test
    fun `opening a technique the game does not have changes nothing`() = runTest {
        val vm = model()

        vm.openTechnique(queens, TechniqueId("pairs"))
        vm.openTechnique(GameId("minisudoku"), TechniqueId("singles"))

        assertNull(vm.current.open)
    }

    @Test
    fun `opening the technique that is already open keeps its round, and opening it again after a close starts a new one`() = runTest {
        val vm = model()
        vm.openTechnique(queens, TechniqueId("singles"))
        val first = vm.current.open!!.attempt

        vm.openTechnique(queens, TechniqueId("singles"))
        assertEquals("the same technique again is no new attempt", first, vm.current.open!!.attempt)

        vm.closeTechnique()
        assertNull(vm.current.open)
        vm.openTechnique(queens, TechniqueId("singles"))
        assertEquals("a reopened technique is a new attempt", first + 1, vm.current.open!!.attempt)
    }

    @Test
    fun `opening another technique is a new attempt`() = runTest {
        val vm = model()
        vm.openTechnique(queens, TechniqueId("singles"))
        val first = vm.current.open!!

        vm.openTechnique(queens, TechniqueId("squeeze"))

        assertEquals("Squeeze", vm.current.open!!.technique.name)
        assertEquals(first.attempt + 1, vm.current.open!!.attempt)
    }

    @Test
    fun `picking another game closes the technique open in this one, picking the same game keeps it`() = runTest {
        val vm = model()
        vm.openTechnique(queens, TechniqueId("singles"))

        vm.selectGame(queens)
        assertNotNull("the same game keeps the round", vm.current.open)

        vm.selectGame(tango)
        assertNull("another game closes it", vm.current.open)
    }

    @Test
    fun `the open technique survives a new view model on the same handle, as after a rotation or a restore`() = runTest {
        val handle = SavedStateHandle()
        val before = model(handle = handle)
        before.openTechnique(queens, TechniqueId("squeeze"))
        val attempt = before.current.open!!.attempt

        val after = model(handle = handle)

        assertEquals("Squeeze", after.current.open!!.technique.name)
        assertEquals(attempt, after.current.open!!.attempt)
    }

    @Test
    fun `a restored attempt is not reused by the next opening`() = runTest {
        val handle = SavedStateHandle()
        val before = model(handle = handle)
        before.openTechnique(queens, TechniqueId("squeeze"))
        val attempt = before.current.open!!.attempt

        val after = model(handle = handle)
        after.openTechnique(queens, TechniqueId("singles"))

        assertEquals(attempt + 1, after.current.open!!.attempt)
    }

    @Test
    fun `a closed technique is not restored`() = runTest {
        val handle = SavedStateHandle()
        val before = model(handle = handle)
        before.openTechnique(queens, TechniqueId("squeeze"))
        before.closeTechnique()

        assertNull(model(handle = handle).current.open)
    }

    @Test
    fun `a stored technique whose game or technique is gone is not restored`() = runTest {
        val handle = SavedStateHandle()
        model(handle = handle).openTechnique(queens, TechniqueId("squeeze"))

        val withoutGame = TrainerRegistry(listOf(tangoGame), listOf(tango))
        val withoutTechnique = TrainerRegistry(listOf(FakeTrainerGame(queens, techniques("singles"))), listOf(queens))

        assertNull(model(registry = withoutGame, handle = handle).current.open)
        assertNull(model(registry = withoutTechnique, handle = handle).current.open)
    }
}
