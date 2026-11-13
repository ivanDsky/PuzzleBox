package com.ivandsky.puzzlebox.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.app.play.freshDailyForAllGames
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import androidx.compose.runtime.DisposableEffect
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Polish 04 over the real shell: opening a board takes the tab bar (phone) or rail (tablet)
 * away as motion, never as a one-frame cut. Mid-exit the chrome is still on screen, part-way
 * along its exit; once the move settles it is gone. Data and backend singles are fakes over
 * the production graph.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class ChromeExitShellTest {

    // Koin is the outer rule: the activity and its composition are torn down while the graph
    // is still up. As an @After, stopKoin() closed the scopes first, and the disposing
    // composition (ViewModels, koinInject) then resolved from a closed scope.
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(KoinGraph()).around(composeRule)

    private lateinit var navigator: Navigator

    private class KoinGraph : ExternalResource() {
        override fun before() {
            stopKoin()
            startKoin {
                androidContext(RuntimeEnvironment.getApplication())
                modules(
                    PuzzleBoxApplication.appModules + module {
                        single<DailyRepository> { freshDailyForAllGames() }
                        single<CompletionRepository> { FakeCompletionRepository() }
                        single<SavedPlayRepository> { FakeSavedPlayRepository() }
                        single<IdentityService> { FakeIdentityService() }
                        single<LeaderboardRepository> { FakeLeaderboardRepository() }
                    },
                )
            }
        }

        override fun after() {
            stopKoin()
        }
    }

    @Test
    fun `phone bar parallaxes out with the hub as a board slides over it`() {
        showHub(WindowWidthSizeClass.Compact)
        val rest = left(PLAY_TAB)

        openBoard(MID_EXIT_MILLIS)

        assertEquals("the bar is still on screen mid-exit", 1, count(PLAY_TAB))
        val shift = rest - left(PLAY_TAB)
        assertTrue("the bar has started toward the start edge: $shift", shift > 0f)
        val window = composeRule.onRoot().getBoundsInRoot().let { it.right - it.left }.value
        assertTrue("…by at most the parallax: $shift", shift <= window * MotionTokens.NavParallaxFraction + EPS)

        settle()
        assertEquals("the bar is gone once the board is up", 0, count(PLAY_TAB))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `tablet rail fades and shifts out early in the lift`() {
        showHub(WindowWidthSizeClass.Expanded)
        val rest = left(PLAY_TAB)

        openBoard(MID_EXIT_MILLIS)

        assertEquals("the rail is still on screen mid-exit", 1, count(PLAY_TAB))
        val shift = rest - left(PLAY_TAB)
        assertTrue("the rail shifts toward the start: $shift", shift > 0f)
        assertTrue("by at most its shift token: $shift", shift <= MotionTokens.LiftNavShift.value + EPS)

        settle()
        assertEquals("the rail is gone once the board is up", 0, count(PLAY_TAB))
    }

    private fun showHub(width: WindowWidthSizeClass) {
        composeRule.setContent {
            DisposableEffect(Unit) {
                onDispose { disposals += GlobalContext.getOrNull() != null }
            }
            PuzzleBoxTheme(darkTheme = false) {
                PuzzleBoxApp(
                    windowWidthSizeClass = width,
                    navigator = rememberNavigator(puzzleBoxNavigationState().selectTab(PlayKey)).also { navigator = it },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    // With the clock paused nothing idles the main looper, so the platform's deferred
    // snapshot apply never runs; apply the push ourselves or the move is never seen.
    private fun openBoard(sampleAfterMillis: Long) {
        composeRule.runOnIdle {
            navigator.push(GameKey(ZIP))
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(sampleAfterMillis)
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }
    private fun count(tag: String): Int = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    // Unclipped: a rail sliding past the window's start edge is clipped there.
    private fun left(tag: String): Float = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().left.value

    private companion object {
        /** Whether Koin was still up as each test's composition was disposed. */
        val disposals = mutableListOf<Boolean>()

        @AfterClass
        @JvmStatic
        fun compositionsDisposedBeforeKoinStopped() {
            assertTrue("every composition disposes with Koin still up: $disposals", disposals.isNotEmpty() && disposals.all { it })
        }

        const val ZIP = "zip"
        const val PLAY_TAB = "nav.tab.play"

        /** Inside both exits: the bar's slide and the rail's early fade. */
        const val MID_EXIT_MILLIS = 64L
        const val SETTLE_MILLIS = 3_000L
        const val EPS = 0.5f
    }
}
