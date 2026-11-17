package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The caption slot at 360 dp: the longest hint explanation on line one, the cost line on line two. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class HintCostLineGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `longest explanation over the cost line`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Surface {
                    PlayCaptionSlot(
                        caption = "There's no simple step from here, so here's one cell to get you going: row 9, column 9 is 9.",
                        note = HINT_COST_TEXT,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play.caption_slot").captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/HintCostLine_slot.png")
                .canonicalFile
                .absolutePath,
        )
    }
}
