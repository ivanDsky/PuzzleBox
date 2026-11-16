package com.ivandsky.puzzlebox.app.play

import com.ivandsky.puzzlebox.app.di.PreferencesQueensSettings
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.di.domainModule
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensPlayUi
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayUi
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.games.sudokuui.SudokuHighlightSettings
import com.ivandsky.puzzlebox.games.sudokuui.SudokuPlayUi
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayUi
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * The real five-game graph the board-first tests open plays in: the real play store over [daily], every game's
 * real controller, fakes for everything else. [daily] is scripted before [start].
 */
class RealGamesGraph(private val daily: DailyRepository, private val day: LocalDate) {

    private var koin: KoinApplication? = null

    fun start() {
        stopKoin()
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default) }
                    single { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<ReleaseFlags> { FakeReleaseFlags() }
                    single { GameRegistry(listOf(zipPlugin, tangoPlugin, queensPlugin, sudokuPlugin(SudokuGames.MINI), strandsPlugin)) }
                    single {
                        GamePlayRegistry(
                            listOf(
                                ZipPlayUi(),
                                TangoPlayUi(HintMode.Stepwise(get<DispatcherProvider>().default)),
                                QueensPlayUi(PreferencesQueensSettings(FakeGameSettingsRepository(), CoroutineScope(Dispatchers.Unconfined)), HintMode.Stepwise(get<DispatcherProvider>().default)),
                                SudokuPlayUi(SudokuGames.MINI, DefaultTints),
                                StrandsPlayUi(),
                            ),
                        )
                    }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<MatchLocalRepository> { FakeMatchLocalRepository() }
                    single<MatchResultOutbox> { FakeMatchResultOutbox() }
                    single<IdentityService> { FakeIdentityService() }
                    single<GameCalendar> { FakeGameCalendar(day) }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                },
                domainModule,
                playModule,
            )
        }
    }

    fun open(game: GameId): ActivePlay = koin!!.koin.get<PlaySessionStore>().open(GameKey(game.value, day.toString()))!!

    fun stop() = stopKoin()

    private object DefaultTints : SudokuHighlightSettings {
        override val peer: Flow<Boolean> = flowOf(true)
        override val sameDigit: Flow<Boolean> = flowOf(true)

        override suspend fun setPeer(enabled: Boolean) = Unit

        override suspend fun setSameDigit(enabled: Boolean) = Unit
    }
}

/** Polls [condition] on the test thread: the real controllers run on real dispatchers. */
fun awaitUntil(what: String, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + 10_000
    while (!condition()) {
        check(System.currentTimeMillis() < end) { "timed out: $what" }
        Thread.sleep(10)
    }
}
