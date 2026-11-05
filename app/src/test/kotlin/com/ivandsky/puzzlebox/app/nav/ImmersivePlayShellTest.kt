package com.ivandsky.puzzlebox.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.app.play.freshDailyForAllGames
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Immersive play (A15, N2) over the real shell: the tab bar is gone while the selected
 * tab's top is a board or a result, shows on every other destination, and system back
 * from a board lands on the hub with the bar back. Data and backend singles are fakes
 * over the production graph.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class ImmersivePlayShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navigator: Navigator

    @Before
    fun startGraph() {
        stopKoin()
        startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    // Home prefetches today's puzzles on resume: every game's fetch is scripted.
                    single<DailyRepository> { freshDailyForAllGames() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository() }
                },
            )
        }
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    @Composable
    private fun Shell(initial: NavigationState) {
        PuzzleBoxTheme(darkTheme = false) {
            PuzzleBoxApp(
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                navigator = rememberNavigator(initial).also { navigator = it },
            )
        }
    }

    /** Every tab item is on screen once: the bar shows. */
    private fun assertBarShown(on: NavigationState) {
        topLevelDestinations.forEach { destination ->
            assertEquals("${destination.tag} on $on", 1, tabNodes(destination))
        }
    }

    /** No tab item is on screen: the bar is hidden. */
    private fun assertBarHidden(on: NavigationState) {
        topLevelDestinations.forEach { destination ->
            assertEquals("${destination.tag} on $on", 0, tabNodes(destination))
        }
    }

    private fun tabNodes(destination: TopLevelDestination): Int =
        composeRule.onAllNodesWithTag(destination.tag).fetchSemanticsNodes().size

    private fun state(): NavigationState = composeRule.runOnIdle { navigator.state }

    @Test
    fun `the bar hides on a board and a result and shows everywhere else`() {
        composeRule.setContent { Shell(puzzleBoxNavigationState()) }
        // Each move, then whether the bar shows on the selected tab's top afterwards.
        fun step(shown: Boolean, move: Navigator.() -> Unit) = move to shown
        listOf(
            step(true) {}, // Home
            step(true) { push(HistoryKey()) },
            step(false) { push(GameKey(ZIP)) }, // a History replay on Home's stack
            step(true) { selectTab(PlayKey) }, // the hub
            step(false) { push(GameKey(ZIP)) },
            step(false) { replaceTop(RESULT) },
            step(true) { pop() }, // back on the hub
            step(true) { selectTab(ProfileKey) },
            step(true) { push(CatalogKey) },
            step(false) { selectTab(HomeKey) }, // Home's top is still the replay
        ).forEach { (move, shown) ->
            composeRule.runOnIdle { navigator.move() }
            composeRule.waitForIdle()
            if (shown) assertBarShown(state()) else assertBarHidden(state())
        }
    }

    @Test
    fun `back from a board returns to the hub with the bar shown`() {
        val inGame = puzzleBoxNavigationState().selectTab(PlayKey).push(GameKey(ZIP))
        composeRule.setContent { Shell(inGame) }
        composeRule.waitForIdle()
        assertBarHidden(inGame)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf<NavKey>(PlayKey), state().currentStack)
        assertEquals(PlayKey, state().tab)
        assertBarShown(state())
        composeRule.onNodeWithTag(PLAY_TAB).assertIsSelected()
    }

    private companion object {
        const val ZIP = "zip"
        const val PLAY_TAB = "nav.tab.play"
        val RESULT = ResultKey(ZIP, "2026-03-09", timeMs = 1_000L, hintsUsed = 0)
    }
}
