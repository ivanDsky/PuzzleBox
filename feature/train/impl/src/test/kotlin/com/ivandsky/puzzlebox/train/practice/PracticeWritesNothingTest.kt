package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerProgressRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.FakeTrainerArchive
import com.ivandsky.puzzlebox.train.FakeTrainerGame
import com.ivandsky.puzzlebox.train.InlineDispatcherProvider
import com.ivandsky.puzzlebox.train.di.trainModule
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.LINE_LOCK
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.QUEENS
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import com.ivandsky.puzzlebox.train.techniques
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

class PracticeWritesNothingTest {

    @get:Rule
    val main = MainDispatcherRule()

    @After
    fun tearDown() = stopKoin()

    private val completions = FakeCompletionRepository()
    private val savedPlays = FakeSavedPlayRepository()
    private val daily = FakeDailyRepository()
    private val progress = FakeTrainerProgressRepository()
    private val game = FakeTrainerGame(QUEENS, techniques("line-lock"))

    /** The practice view model as the app builds it: off the Train module, in a graph where every play writer is bound. */
    private fun practiceFromTheGraph(): PracticeViewModel {
        stopKoin() // tests share the JVM-global Koin context
        val koin = startKoin {
            modules(
                trainModule,
                module {
                    single<TrainerArchive> { FakeTrainerArchive() }
                    single<TrainerPuzzleSource> { TrainerPuzzleSource { flow { emit(puzzle(1)); emit(puzzle(2)) } } }
                    single<TrainerProgressRepository> { progress }
                    single<DispatcherProvider> { InlineDispatcherProvider }
                    single<CompletionRepository> { completions }
                    single<SavedPlayRepository> { savedPlays }
                    single<DailyRepository> { daily }
                },
            )
        }.koin
        return koin.get<PracticeViewModel> { parametersOf(game, LINE_LOCK) }
    }

    /**
     * Catches a practice that leaves a trace of the player's play: a finished board writing a completion, record or
     * replay (so a streak day or a best), a move saving a play slot, or the board touching the daily's cache. A board
     * played through every turn, finished and left for the next one, leaves the completion, saved-play and daily stores as
     * they began; the only write is the trainer's own progress.
     */
    @Test
    fun `a played and finished board leaves no completion, saved play or daily behind`() = runTest {
        val vm = practiceFromTheGraph()
        vm.start(1)
        val board = game.boards.single()

        board.turn(TrainerVerdict.Right)
        board.turn(TrainerVerdict.Wrong)
        board.turn(TrainerVerdict.ShowMe)
        board.say(PracticeStatus.Done(null))
        main.testDispatcher.scheduler.advanceTimeBy(BOARD_DONE_PAUSE_MS)
        main.testDispatcher.scheduler.runCurrent()
        assertEquals("the next board opened on its own", 2, game.boards.size)

        assertTrue(completions.observeRecords().first().isEmpty())
        assertTrue(completions.observeImports().first().isEmpty())
        assertEquals(emptyMap<Any, Any>(), savedPlays.rows)
        assertEquals(emptyMap<Any, Any>(), savedPlays.observeInProgress().first())
        assertEquals(0, daily.todayCalls(QUEENS))
        assertEquals(emptyList<Any>(), daily.entries(QUEENS))
        assertEquals(setOf(QUEENS), progress.stored.keys)
        assertEquals(setOf(LINE_LOCK), progress.stored.getValue(QUEENS).keys)
        assertEquals(TechniqueProgress.Mark.Practicing, progress.stored.getValue(QUEENS).getValue(LINE_LOCK).mark)
    }

    /**
     * Catches a play repository, store or outbox added to the practice view model's constructor later: its data
     * dependencies are the puzzle source, the archive and the trainer's own progress, none of which is the player's
     * records.
     */
    @Test
    fun `the practice view model takes no play repository, store or outbox`() {
        val parameterTypes = PracticeViewModel::class.java.declaredConstructors.flatMap { it.parameterTypes.toList() }.map { it.simpleName }

        assertTrue(parameterTypes.containsAll(listOf("TrainerArchive", "TrainerPuzzleSource", "TrainerProgressRepository")))
        val writers = parameterTypes.filter { name -> listOf("Repository", "Store", "Outbox").any(name::endsWith) }
            .filterNot { it == "TrainerProgressRepository" }
        assertEquals(emptyList<String>(), writers)
    }
}
