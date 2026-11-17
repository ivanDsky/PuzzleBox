package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.LocalRouteCaption
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import kotlin.time.Duration.Companion.seconds
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

/** A self-duel leg's board chrome (top bar, time-left side panel on a tablet): every clickable is a 48 dp touch target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class LegBoardTouchTargetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun bind() {
        stopKoin()
        startKoin { modules(module {}) }
    }

    @After
    fun unbind() = stopKoin()

    private fun show(layout: PlayLayout) {
        val game = TestGames.alpha
        val ui = FakePlayUi(game)
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Job()), onSessionChanged = {}, onWon = {})) as FakeController
        controller.adoptToday(TestGames.puzzle(game), TestGames.Ref(game, PuzzleKey.Custom("draft-castle")))
        controller.advance(88_000L)
        val leg = SelfLeg(target = 134.seconds, played = 1, count = 3)
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalRouteCaption provides "Duel vs Past you · puzzle 2 of 3") {
                    PlayBoardChrome(
                        ui = ScaffoldedUi(ui),
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        title = "Zip · Tuesday",
                        label = null,
                        pastDay = false,
                        tutorial = TutorialGate.Closed,
                        onHowToPlay = null,
                        onTutorialFinished = {},
                        layout = layout,
                        onBack = {},
                        onRetry = {},
                        onSolved = { _, _ -> },
                        overflow = listOf(PlayOverflowItem("Settings", checked = null, onClick = {})),
                        sidePanel = { clock, showTimer -> legSidePanel(leg, clock, { 0 }, showTimer) },
                        modifier = Modifier,
                        targetMs = leg.target?.inWholeMilliseconds,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `phone leg board's top bar controls are 48 dp touch targets`() {
        show(PlayLayout.SinglePane)

        rule.assertTouchTargetsAtLeast48()
    }

    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    @Test
    fun `tablet leg board's top bar and side panel controls are 48 dp touch targets`() {
        show(PlayLayout.TwoPane)

        rule.assertTouchTargetsAtLeast48()
    }
}
