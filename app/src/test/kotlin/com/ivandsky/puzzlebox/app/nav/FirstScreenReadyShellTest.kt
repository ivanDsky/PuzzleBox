package com.ivandsky.puzzlebox.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.e2e.E2E_TODAY
import com.ivandsky.puzzlebox.app.e2e.E2eKoinGraph
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.FirstScreenReady
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell's half of the first-screen-ready signal: a landing on any screen but Home is ready at
 * first composition, while a Home landing waits for Home's own local state.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class FirstScreenReadyShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var graph: E2eKoinGraph? = null

    @After
    fun stopGraph() {
        graph?.stop()
    }

    private fun launch(
        link: String? = null,
        localReadsNeverFinish: Boolean = false,
        state: NavigationState = puzzleBoxNavigationState(),
    ): FirstScreenReady {
        val started = E2eKoinGraph(
            E2E_TODAY,
            overrides = module {
                single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                if (localReadsNeverFinish) {
                    single<CompletionRepository> { SilentCompletionRepository(FakeCompletionRepository()) }
                }
            },
        ).also { graph = it }
        started.start()
        val ready = started.koin.get<FirstScreenReady>()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PuzzleBoxApp(
                    windowWidthSizeClass = WindowWidthSizeClass.Compact,
                    navigator = rememberNavigator(state),
                    pendingLink = link,
                    firstScreenReady = ready,
                )
            }
        }
        composeRule.waitForIdle()
        return ready
    }

    @Test
    fun `a friend-link launch publishes at first composition without waiting for Home`() {
        val ready = launch(link = "${ShareCode.LINK_PREFIX}K7Q2MX", localReadsNeverFinish = true)

        assertTrue(ready.isReady)
    }

    @Test
    fun `a launch onto the Profile layer over Today publishes at first composition without waiting for Home`() {
        val ready = launch(state = puzzleBoxNavigationState().copy(layer = listOf(ProfileKey)), localReadsNeverFinish = true)

        assertTrue(ready.isReady)
    }

    @Test
    fun `a Home launch does not publish before Home's local state has loaded`() {
        val ready = launch(localReadsNeverFinish = true)

        assertFalse(ready.isReady)
    }

    @Test
    fun `a Home launch is ready once Home's local state has loaded`() {
        val ready = launch()

        assertTrue(ready.isReady)
    }

    private class SilentCompletionRepository(inner: CompletionRepository) : CompletionRepository by inner {
        override fun observeRecords() = emptyFlow<List<CompletionRecord>>()
    }
}
