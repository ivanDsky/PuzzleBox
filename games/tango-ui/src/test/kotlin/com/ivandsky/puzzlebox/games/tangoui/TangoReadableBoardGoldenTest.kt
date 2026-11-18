package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tango board sample, light and dark: an ink wall for the frame, the
 * quiet grid line, givens with no wash or outline, signs in ink. [TangoBoardGoldenTest] pins the full
 * renderer beside it. Rendered with `-Proborazzi.record=true` into `snapshots/` (untracked review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class TangoReadableBoardGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean) {
        composeRule.setContent { TangoReadableBoardSample(dark) }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(File("snapshots/$name").apply { parentFile?.mkdirs() })
    }

    @Test fun `golden readable board light`() = capture("tango-readable-board-light.png", dark = false)

    @Test fun `golden readable board dark`() = capture("tango-readable-board-dark.png", dark = true)
}
