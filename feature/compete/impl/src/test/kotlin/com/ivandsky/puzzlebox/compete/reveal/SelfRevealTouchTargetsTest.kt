package com.ivandsky.puzzlebox.compete.reveal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.compete.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The self duel's summary (Rematch, Watch again, Done, each Watch compare row) on phone and tablet: all 48 dp touch targets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SelfRevealTouchTargetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(wide: Boolean) {
        rule.setContent {
            PuzzleBoxTheme {
                RevealSummary(
                    summary = RevealSelfGoldenScreenshots.WIN,
                    brands = goldenBrands(),
                    wide = wide,
                    onBack = {},
                    onRematch = {},
                    onWatchAgain = {},
                    compares = setOf(0, 1, 2),
                    onCompare = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `phone summary controls are 48 dp touch targets`() {
        show(wide = false)

        rule.onNodeWithText("Rematch").assertExists()
        rule.assertTouchTargetsAtLeast48()
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test
    fun `tablet summary controls are 48 dp touch targets`() {
        show(wide = true)

        rule.onNodeWithText("Rematch").assertExists()
        rule.assertTouchTargetsAtLeast48()
    }
}
