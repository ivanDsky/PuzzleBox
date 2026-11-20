package com.ivandsky.puzzlebox.train.drill

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.FakeTrainerArchive
import com.ivandsky.puzzlebox.train.FakeTrainerGame
import com.ivandsky.puzzlebox.train.InlineDispatcherProvider
import com.ivandsky.puzzlebox.train.di.trainModule
import com.ivandsky.puzzlebox.train.techniques
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

class DrillWritesNothingTest {

    @get:Rule
    val main = MainDispatcherRule()

    @After
    fun tearDown() = stopKoin()

    private val queens = GameId("queens")
    private val technique = TechniqueId("line-lock")
    private val completions = FakeCompletionRepository()
    private val savedPlays = FakeSavedPlayRepository()
    private val daily = FakeDailyRepository()
    private val archive = FakeTrainerArchive()
    private val ui = FakeGamePlayUi(queens)

    /** The drill view model as the app builds it: off the Train module, in a graph where every play writer is bound. */
    private fun drillFromTheGraph(): DrillViewModel {
        val game = FakeTrainerGame(
            queens,
            techniques("line-lock"),
            drillSources = listOf(DrillSource { listOf(DrillCandidate.Archive(number = 9, targetSteps = 4)) }),
        )
        stopKoin() // tests share the JVM-global Koin context
        val koin = startKoin {
            modules(
                trainModule,
                module {
                    single { GamePlayRegistry(listOf(ui)) }
                    single<GameRegistry> { TestGames.registry(listOf(queens)) }
                    single<TrainerArchive> { archive }
                    single<DispatcherProvider> { InlineDispatcherProvider }
                    single<CompletionRepository> { completions }
                    single<SavedPlayRepository> { savedPlays }
                    single<DailyRepository> { daily }
                },
            )
        }.koin
        return koin.get<DrillViewModel> { parametersOf(game, technique) }
    }

    /**
     * Catches a drill that leaves a trace of the player's practice: a win writing a completion, record or replay
     * (so a streak day or a best), a move saving a play slot, or the throwaway board touching the daily's cache. A
     * played and won drill, left by back, leaves the completion, saved-play and daily stores as they began, and the board
     * records no replay.
     */
    @Test
    fun `a played and won drill leaves no completion, saved play or daily behind`() = runTest {
        archive.cached += 9
        val vm = drillFromTheGraph()
        vm.start(1)
        val controller = ui.controllers.single()

        controller.change()
        controller.win()
        vm.solved()
        vm.leave()

        assertTrue(completions.observeRecords().first().isEmpty())
        assertTrue(completions.observeImports().first().isEmpty())
        assertTrue(completions.observeCustom(queens, "trainer-queens-9").first().isEmpty())
        assertEquals(emptyMap<Any, Any>(), savedPlays.rows)
        assertEquals(emptyMap<Any, Any>(), savedPlays.observeInProgress().first())
        assertEquals(0, daily.todayCalls(queens))
        assertEquals(emptyList<Any>(), daily.entries(queens))
        assertNull(controller.env.moveLog.snapshot())
        assertNull(controller.env.moveLog.seal(ReplayOutcome.Solved, endMs = 1_000L))
    }

    /**
     * Catches a drill that plays under the day's or an archive puzzle's own key, which would stamp that puzzle done,
     * and a throwaway name that is not the game's and the puzzle's.
     */
    @Test
    fun `the drill plays under a throwaway custom key of its game and puzzle`() {
        archive.cached += 9
        val vm = drillFromTheGraph()

        vm.start(1)

        val ref = ui.controllers.single().adopted.single().second
        assertEquals(PuzzleKey.Custom("trainer-queens-9"), ref.key)
        assertEquals(queens, ref.gameId)
    }

    /**
     * Catches a repository, store or outbox added to the drill's constructor later: the drill's one data dependency is
     * the trainer archive, whose cache is not the player's records.
     */
    @Test
    fun `the drill view model takes no repository, store or outbox`() {
        val parameterTypes = DrillViewModel::class.java.declaredConstructors.flatMap { it.parameterTypes.toList() }.map { it.simpleName }

        assertTrue(parameterTypes.contains("TrainerArchive"))
        val writers = parameterTypes.filter { name -> listOf("Repository", "Store", "Outbox", "Source").any(name::endsWith) }
        assertEquals(emptyList<String>(), writers)
    }
}
