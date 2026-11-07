package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A7 / spec §2: the board never moves — the chrome's caption slot reserves two lines of
 * bodyMedium at the current font scale, so a caption appearing leaves the board's bounds
 * exactly where they were.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayCaptionSlotTest {

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

    private val twoLines =
        "These two cells cannot both hold a sun, because three in a row is never allowed on the board here"

    private fun setScreen(fontScale: Float, feedback: PlayFeedback, hints: PlayHints) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                PuzzleBoxTheme(darkTheme = false) {
                    AdaptivePlayScaffold(
                        layout = PlayLayout.SinglePane,
                        modifier = Modifier.requiredSize(width = 360.dp, height = 800.dp),
                        board = { Box(Modifier.fillMaxSize().testTag("board")) },
                        side = {
                            PlayChrome(
                                clock = null,
                                hints = hints,
                                banner = null,
                                feedback = feedback,
                            )
                        },
                    )
                }
            }
        }
    }

    private fun assertBoardStillWhileCaptionChanges(fontScale: Float) {
        val feedback = FakeFeedback()
        val hints = FakeHints()
        setScreen(fontScale, feedback, hints)
        val empty = composeRule.onNodeWithTag("board").getBoundsInRoot()
        assertTrue("the slot reserves height while empty", empty.bottom < 800.dp)

        feedback.violation = twoLines
        composeRule.waitForIdle()
        composeRule.onNodeWithText(twoLines).assertExists()
        assertEquals("violation caption", empty, composeRule.onNodeWithTag("board").getBoundsInRoot())

        feedback.violation = null
        hints.hintCaption = "Hint — the red region"
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hint — the red region").assertExists()
        assertEquals("hint caption", empty, composeRule.onNodeWithTag("board").getBoundsInRoot())

        hints.hintCaption = null
        composeRule.waitForIdle()
        assertEquals("cleared caption", empty, composeRule.onNodeWithTag("board").getBoundsInRoot())
    }

    @Test
    fun `board bounds hold at font scale 1`() = assertBoardStillWhileCaptionChanges(1f)

    @Test
    fun `board bounds hold at font scale 2`() = assertBoardStillWhileCaptionChanges(2f)

    @Test
    fun `slot is a polite live region`() {
        setScreen(1f, FakeFeedback().apply { rejection = "Not there" }, FakeHints())
        composeRule.onNodeWithTag("play.caption_slot")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertHasNoClickAction()
        composeRule.onNodeWithText("Not there").assertExists()
    }
}
