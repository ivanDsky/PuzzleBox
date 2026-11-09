package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The route caption is the lowest-priority source of the one caption slot: the game's own
 * violation, rejection and hint captions win while they show, and the route line returns
 * when they clear. With no provider the slot is empty, exactly as before.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RouteCaptionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeFeedback : PlayFeedback {
        override var violation: String? by mutableStateOf(null)
        override var rejection: String? by mutableStateOf(null)
    }

    private class FakeHints : PlayHints {
        override val hintsUsed = 0
        override val hintEnabled = true
        override var hintCaption: String? by mutableStateOf(null)
        override fun requestHint() = Unit
    }

    private val route = "Duel vs Sam · puzzle 1 of 3"

    /** [routeCaption] null means no [LocalRouteCaption] provider at all (the default). */
    private fun setScreen(routeCaption: String?, feedback: PlayFeedback, hints: PlayHints) {
        composeRule.setContent {
            val screen: @Composable () -> Unit = {
                PuzzleBoxTheme(darkTheme = false) {
                    AdaptivePlayScaffold(
                        layout = PlayLayout.SinglePane,
                        modifier = Modifier.requiredSize(width = 360.dp, height = 800.dp),
                        board = { Box(Modifier.fillMaxSize()) },
                        side = {
                            PlayChrome(
                                clock = null,
                                hints = hints,
                                banner = null,
                                feedback = feedback,
                            )
                        },
                        sidePanel = null,
                    )
                }
            }
            if (routeCaption == null) {
                screen()
            } else {
                CompositionLocalProvider(LocalRouteCaption provides routeCaption) { screen() }
            }
        }
    }

    @Test
    fun `routeCaptionShowsWhenFeedbackAndHintsAreEmpty`() {
        setScreen(route, FakeFeedback(), FakeHints())
        composeRule.onNodeWithText(route).assertExists()
    }

    @Test
    fun `violationCaptionWinsOverRouteCaptionThenYieldsBack`() {
        val feedback = FakeFeedback()
        setScreen(route, feedback, FakeHints())

        composeRule.runOnIdle { feedback.violation = "Three suns in a row" }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Three suns in a row").assertExists()
        composeRule.onNodeWithText(route).assertDoesNotExist()

        composeRule.runOnIdle { feedback.violation = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Three suns in a row").assertDoesNotExist()
        composeRule.onNodeWithText(route).assertExists()
    }

    @Test
    fun `hintCaptionWinsOverRouteCaption`() {
        val hints = FakeHints().apply { hintCaption = "Hint: the red region" }
        setScreen(route, FakeFeedback(), hints)
        composeRule.onNodeWithText("Hint: the red region").assertExists()
        composeRule.onNodeWithText(route).assertDoesNotExist()
    }

    @Test
    fun `noProviderShowsNoCaption`() {
        setScreen(null, FakeFeedback(), FakeHints())
        composeRule.onNodeWithTag("play.caption_slot")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }
}
