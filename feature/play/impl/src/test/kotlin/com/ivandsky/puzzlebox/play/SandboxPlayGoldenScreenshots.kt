package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The play screen a test solve opens on (ticket 98), phone and tablet, light and dark: the play top bar names the
 * draft and reads "Test solve" where a daily reads its number, with Up and the glyph before it and the timer after,
 * on the tablet too, where no side panel takes the timer. The board under it is the stand-in game's. `top bar no
 * clock` is the Trainer's drill (ticket 13): the same bar, labelled "Practice puzzle", with the timer left out.
 * Goldens in `snapshots/sandbox-play/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class SandboxPlayGoldenScreenshotsPhone : SandboxPlayGoldenScreenshots("phone", PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class SandboxPlayGoldenScreenshotsTablet : SandboxPlayGoldenScreenshots("tablet", PlayLayout.TwoPane)

abstract class SandboxPlayGoldenScreenshots(private val device: String, private val layout: PlayLayout) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun bind() {
        stopKoin() // tests share the JVM-global Koin context
        startKoin { modules(module {}) }
    }

    @After
    fun unbind() = stopKoin()

    @Test fun `top bar light`() = capture(dark = false)
    @Test fun `top bar dark`() = capture(dark = true)
    @Test fun `top bar no clock light`() = capture(dark = false, showClock = false)
    @Test fun `top bar no clock dark`() = capture(dark = true, showClock = false)

    private fun capture(dark: Boolean, showClock: Boolean = true) {
        val game = TestGames.alpha
        val ui = FakePlayUi(game)
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Job()), onSessionChanged = {}, onWon = {})) as FakeController
        controller.adoptToday(TestGames.puzzle(game), TestGames.Ref(game, PuzzleKey.Custom("draft-castle")))
        controller.advance(ELAPSED_MS)
        rule.showGolden(dark) {
            SandboxPlay(
                ui, controller, title = "Castle", label = if (showClock) "Test solve" else "Practice puzzle",
                layout = layout, onBack = {}, onSolved = {}, showClock = showClock,
            )
        }
        rule.settleAndCapture("sandbox-play", if (showClock) "top-bar" else "top-bar-no-clock", device, dark)
    }

    private companion object {
        /** 1:23 on the timer. */
        const val ELAPSED_MS = 83_000L
    }
}
