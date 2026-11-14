package com.ivandsky.puzzlebox.app.nav

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.app.play.freshDailyForAllGames
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The real shell over the production graph with core:testing fakes: leaving a game ends
 * in a resumable save (A10, SC5). A popped mid-game Zip is released by the shell's
 * retain and its latest session lands in the saved-play repository; an activity stop
 * flushes a change still inside the throttle window.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class ShellSessionLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val saved = FakeSavedPlayRepository()
    private val zipKey = GameKey(ZipDefinition.id.value)

    private lateinit var navigator: Navigator

    @Before
    fun startGraph() {
        stopKoin()
        startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<DailyRepository> { freshDailyForAllGames() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<SavedPlayRepository> { saved }
                    single<GameCalendar> { FakeGameCalendar(ZipDefinition.cadence.today()) }
                    single<IdentityService> { FakeIdentityService() }
                    // Every other flag stays off: this path is about the board, not the gated surfaces.
                    single<com.ivandsky.puzzlebox.common.flags.ReleaseFlags> { com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository() }
                    // Every tutorial seen: this path is about the board; the lessons have their own tests.
                    single<com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository> {
                        com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository(
                            seen = setOf(
                                ZipDefinition.id,
                                com.ivandsky.puzzlebox.games.tango.TangoDefinition.id,
                                com.ivandsky.puzzlebox.games.queens.QueensDefinition.id,
                            ),
                        )
                    }
                },
            )
        }
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    private val store: PlaySessionStore get() = KoinPlatform.getKoin().get()

    /** Shows the shell, opens Zip on the Play tab and makes one move (a hint) mid-game. */
    private fun midGameZip(): ActivePlay {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PuzzleBoxApp(
                    windowWidthSizeClass = WindowWidthSizeClass.Compact,
                    navigator = rememberNavigator().also { navigator = it },
                )
            }
        }
        composeRule.runOnIdle {
            navigator.selectTab(PlayKey)
            navigator.push(zipKey)
        }
        return composeRule.runOnIdle {
            val play = store.open(zipKey)!!
            assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
            (play.controller as ZipPlayController).hint()
            play
        }
    }

    /** The session bytes the controller would save now, as the saved row's envelope carries them beside any replay log. */
    private fun latestSession(play: ActivePlay): ByteArray = (play.controller as ZipPlayController).persist()!!

    private fun savedSession(play: ActivePlay): ByteArray = SessionBlob.decode(saved.rows.getValue(play.slot))!!.session

    @Test
    fun `popping a mid-game zip saves its row`() {
        val play = midGameZip()
        // The release folds the clock before its final save, so the row is the folded session.

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(listOf(PlayKey), navigator.state.currentStack)
            assertArrayEquals(latestSession(play), savedSession(play))
        }
    }

    @Test
    fun `an activity stop writes the pending change`() {
        val play = midGameZip()
        composeRule.runOnIdle {
            assertFalse("the hint is still inside the save window", play.slot in saved.rows)
        }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        // A stopped activity draws no frames: idle the main looper, not the composition.
        shadowOf(Looper.getMainLooper()).idle()

        // The stop folds the clock (ON_PAUSE) before flushing, so the row is the folded session.
        assertArrayEquals(latestSession(play), savedSession(play))
    }
}
