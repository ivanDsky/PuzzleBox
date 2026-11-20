package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sandbox play screen's timer is the caller's choice. Catches a drill that shows the clock the spec hides (§2.3), and a
 * Studio test solve that loses its timer to the new switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SandboxPlayClockTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() = stopKoin()

    private fun showSandbox(showClock: Boolean?) {
        stopKoin() // tests share the JVM-global Koin context
        startKoin { modules(module {}) }
        val game = TestGames.alpha
        val ui = FakePlayUi(game)
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Job()), onSessionChanged = {}, onWon = {}))
        controller.adoptToday(TestGames.puzzle(game), TestGames.Ref(game, PuzzleKey.Custom("trainer-alpha-1")))
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                when (showClock) {
                    null -> SandboxPlay(ui, controller, "Castle", "Test solve", PlayLayout.SinglePane, onBack = {}, onSolved = {})
                    else -> SandboxPlay(ui, controller, "Castle", "Practice puzzle", PlayLayout.SinglePane, onBack = {}, onSolved = {}, showClock = showClock)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the sandbox shows its timer unless the caller hides it`() {
        showSandbox(showClock = null)

        composeRule.onNodeWithTag("play.timer").assertExists()
    }

    @Test
    fun `a sandbox told to hide the clock draws no timer but still draws the board`() {
        showSandbox(showClock = false)

        composeRule.onAllNodesWithTag("play.timer").assertCountEquals(0)
        composeRule.onNodeWithTag("play.sandbox").assertExists()
    }
}
