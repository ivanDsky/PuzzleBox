package com.ivandsky.puzzlebox.games.zipui

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
 * Goldens for the readable Zip board at rest (nav-rethink B3): thinner round-capped walls
 * that stop at the board edge, the outline drawn as a wall, and the ring on the next number,
 * light and dark. They are the board to hold against the approved token sheets.
 * Rendered with `-Proborazzi.record=true` into `games/zip-ui/snapshots/` (untracked review
 * captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class ZipReadableBoardGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(dark: Boolean) {
        composeRule.setContent { ZipReadableBoardSample(dark) }
        val out = File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/readable-board-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
        composeRule.onRoot().captureRoboImage(out.absolutePath)
    }

    @Test fun readableBoardLight() = capture(dark = false)

    @Test fun readableBoardDark() = capture(dark = true)
}
