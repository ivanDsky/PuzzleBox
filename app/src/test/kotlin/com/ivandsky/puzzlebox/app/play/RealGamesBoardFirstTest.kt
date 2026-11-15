package com.ivandsky.puzzlebox.app.play

import com.ivandsky.puzzlebox.app.DrainTestApplication
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
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensPlayUi
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayUi
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.games.sudokuui.SudokuHighlightSettings
import com.ivandsky.puzzlebox.games.sudokuui.SudokuPlayUi
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayUi
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Over every real game's controller: a stored daily is ready at once, before its refresh has answered, and
 * the refresh runs behind it (A3). The swap and its player-input latch are pinned over the stand-in games in
 * feature:play:impl, where a puzzle can differ without a game-specific codec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class RealGamesBoardFirstTest {

    private val day: LocalDate = TangoDefinition.cadence.today()
    private var koin: KoinApplication? = null

    @Before
    fun clearKoin() {
        stopKoin()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun startGraph(daily: DailyRepository) {
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

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out: $what" }
            Thread.sleep(10)
        }
    }

    /** [game]'s stored [puzzle] with its refresh held: the board is Ready, and the refresh is out but unanswered. */
    private fun assertReadyBeforeRefresh(game: GameId, puzzle: Puzzle) {
        val daily = FakeDailyRepository().apply {
            setCached(game, day, puzzle)
            setToday(game, puzzle)
        }
        val gate = daily.holdRefresh(game)
        startGraph(daily)

        val play: ActivePlay = koin!!.koin.get<PlaySessionStore>().open(GameKey(game.value, day.toString()))!!

        awaitUntil("${game.value} refresh started") { daily.todayCalls(game) == 1 }
        awaitUntil("${game.value} ready") { play.board.value.status is PlayBoardStatus.Ready }
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        assertTrue("the refresh is still held", !gate.isCompleted)
        gate.complete(Unit)
        assertEquals(1, daily.todayCalls(game))
    }

    @Test fun `zip shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(ZipDefinition.id, ZipPlayFixture.envelope())

    @Test fun `tango shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(TangoDefinition.id, TangoPlayFixture.envelope())

    @Test fun `queens shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(QueensDefinition.id, QueensPlayFixture.envelope())

    @Test fun `mini sudoku shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(SudokuGames.MINI.id, MiniSudokuPlayFixture.envelope())

    @Test fun `strands shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(StrandsDefinition.id, StrandsPlayFixture.envelope())

    private object DefaultTints : SudokuHighlightSettings {
        override val peer: Flow<Boolean> = flowOf(true)
        override val sameDigit: Flow<Boolean> = flowOf(true)

        override suspend fun setPeer(enabled: Boolean) = Unit

        override suspend fun setSameDigit(enabled: Boolean) = Unit
    }
}
