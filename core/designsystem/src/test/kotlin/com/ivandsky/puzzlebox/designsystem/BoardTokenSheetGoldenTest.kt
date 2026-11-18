package com.ivandsky.puzzlebox.designsystem

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
 * The approval sheet for the readable board tokens, light and dark, legacy beside readable. These are the
 * renders the user reviews before any game adopts the tokens (nav-rethink B3). Recorded with
 * `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class BoardTokenSheetGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `light sheet compares the legacy and readable board`() = capture(dark = false, "BoardTokenSheet_light.png")

    @Test
    fun `dark sheet compares the legacy and readable board`() = capture(dark = true, "BoardTokenSheet_dark.png")

    private fun capture(dark: Boolean, file: String) {
        composeRule.setContent { BoardTokenSheet(dark = dark) }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/$file")
                .canonicalFile
                .absolutePath,
        )
    }
}
