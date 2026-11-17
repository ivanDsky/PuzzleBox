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
 * The Tango board under the readable board tokens (nav-rethink B3), light and dark: an ink wall for the frame, the
 * quiet grid line, givens with no wash or outline, signs in ink. [TangoBoardGoldenTest] keeps the legacy look
 * pixel-exact beside it. Runs only under `-Proborazzi.verify` or `-Proborazzi.record=true` ([GoldenCompare]).
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
        val scratch = File("build/roborecord/$name").apply { parentFile?.mkdirs() }
        composeRule.onRoot().captureRoboImage(scratch)
        GoldenCompare.check(File("src/test/resources/goldens/$name"), scratch, name)
    }

    @Test fun `golden readable board light`() = capture("tango-readable-board-light.png", dark = false)

    @Test fun `golden readable board dark`() = capture("tango-readable-board-dark.png", dark = true)
}
