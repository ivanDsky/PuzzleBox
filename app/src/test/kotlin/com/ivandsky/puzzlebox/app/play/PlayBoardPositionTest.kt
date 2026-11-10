package com.ivandsky.puzzlebox.app.play

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensPlayUi
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayUi
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.navigation.playEntry
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.app.di.PreferencesQueensSettings
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every real game is opened on the same Play stack in turn, at one window size, and its
 * board is read once ready — a phone (one pane) and a tablet (two panes). The shared board
 * pane centres each board vertically in its pane. A15: on the tablet every game's route
 * hands it the live stats panel, whose timer replaces the top bar's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the real PuzzleBoxApplication's startKoin would race this class's own graph.
@Config(sdk = [34], application = android.app.Application::class)
class PlayBoardPositionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Every real game shares the America/Los_Angeles cadence zone. */
    private val cadenceToday: LocalDate = TangoDefinition.cadence.today()

    private val games = listOf(ZipDefinition.id, TangoDefinition.id, QueensDefinition.id)

    private val navigator = Navigator(
        NavigationState(tab = PlayKey, stacks = listOf(listOf(PlayKey, GameKey(games.first().value)))),
    )

    private var koin: KoinApplication? = null

    @Before
    fun startGraph() {
        stopKoin() // tests share the JVM-global Koin context
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { freshDailyForAllGames() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single { GameRegistry(listOf(zipPlugin, tangoPlugin, queensPlugin)) }
                    single { GamePlayRegistry(listOf(ZipPlayUi(), TangoPlayUi(), QueensPlayUi(PreferencesQueensSettings(FakeGameSettingsRepository())))) }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReleaseFlags> { FakeReleaseFlags() }
                    single<GameCalendar> { FakeGameCalendar(cadenceToday) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    // The hub's best times and puzzle numbers.
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    // Every tutorial seen: the boards draw straight away.
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository(seen = games.toSet()) }
                    // The tablet side panel's stats: signed out, so no board is read.
                    factory {
                        ObservePlayStats(get(), get(), get(), get(), ObserveLeaderboard(FakeIdentityService(), FakeLeaderboardRepository(), get(), get()))
                    }
                    single { SubmitStatusTracker() }
                    single<IdentityService> { FakeIdentityService() }
                    single<GoogleIdTokenProvider> { CancelledGoogleIdTokenProvider }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp")
    @Test
    fun `every board is centred vertically in its pane on a phone`() {
        assertBoardsCentred(DpSize(360.dp, 800.dp), PlayLayout.SinglePane)
    }

    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1280dp-h800dp")
    @Test
    fun `every board is centred vertically in its pane on a tablet`() {
        assertBoardsCentred(DpSize(1280.dp, 800.dp), PlayLayout.TwoPane)
    }

    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1280dp-h800dp")
    @Test
    fun `every game on a tablet times itself in the stats panel, not the top bar`() {
        eachGame(DpSize(1280.dp, 800.dp), PlayLayout.TwoPane) { game ->
            boardCentreOffset(game)
            assertEquals("${game.value} panel timers", 1, count("play.stats.timer"))
            assertEquals("${game.value} top bar timers", 0, count("play.timer"))
        }
    }

    private fun assertBoardsCentred(size: DpSize, layout: PlayLayout) {
        eachGame(size, layout) { game ->
            assertEquals("${game.value} board centre vs its pane's (px)", 0f, boardCentreOffset(game), 1f)
        }
    }

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Opens each of [games] in turn on one Play stack at [size] and [layout], handing [check] each once it is on top. */
    private fun eachGame(size: DpSize, layout: PlayLayout, check: (GameId) -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.requiredSize(size)) {
                    NavDisplay(
                        backStack = navigator.state.currentStack,
                        onBack = { navigator.pop() },
                        entryProvider = entryProvider {
                            playEntry(navigator, layout, onOpenHistory = {})
                            entry<ResultKey> { Text("result") }
                        },
                    )
                }
            }
        }
        games.forEach { game ->
            if (game != games.first()) {
                composeRule.runOnIdle { navigator.replaceTop(GameKey(game.value)) }
                // Let the stack transition finish: only the new game's board is left.
                composeRule.waitForIdle()
            }
            check(game)
        }
    }

    /**
     * How far [game]'s board centre sits below its board pane's centre, in root pixels,
     * once its puzzle is ready.
     */
    private fun boardCentreOffset(game: GameId): Float {
        composeRule.waitUntil(10_000) { play(game)?.board?.value?.status is PlayBoardStatus.Ready }
        val prefix = if (game == QueensDefinition.id) "queens" else "play"
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("$prefix.board").fetchSemanticsNodes().size == 1 }
        val board = composeRule.onAllNodesWithTag("$prefix.board").fetchSemanticsNodes().single()
        val pane = composeRule.onAllNodesWithTag("$prefix.board_area").fetchSemanticsNodes().single()
        return (board.positionInRoot.y + board.size.height / 2f) - (pane.positionInRoot.y + pane.size.height / 2f)
    }

    private fun play(game: GameId) = koin!!.koin.get<PlaySessionStore>().peek(PlaySlot.Daily(game, cadenceToday))
}

private object CancelledGoogleIdTokenProvider : GoogleIdTokenProvider {
    override suspend fun idToken(activityContext: Context): GoogleIdToken =
        GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
}
