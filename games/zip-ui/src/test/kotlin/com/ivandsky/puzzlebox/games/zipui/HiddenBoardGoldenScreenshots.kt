package com.ivandsky.puzzlebox.games.zipui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of Zip's board before play, on a phone, light and dark: blurred under the veil and its pill, and sharp.
 * The board is built from the puzzle as it travels, solution and all, through [ZipReplayBoard.givens]; the sharp frames show
 * that only what the puzzle gives away is drawn. The capture draws without the GPU, so the blurred frames pin the
 * picture path. Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class HiddenBoardGoldenScreenshots {

    @get:Rule
    val composeRule = createComposeRule()

    private val envelope: Puzzle = ZipPlayFixture.envelope()

    @Test
    fun `blurred`() = capture(sharp = false, dark = false)

    @Test
    fun `blurred, dark`() = capture(sharp = false, dark = true)

    @Test
    fun `sharp`() = capture(sharp = true, dark = false)

    @Test
    fun `sharp, dark`() = capture(sharp = true, dark = true)

    private fun capture(sharp: Boolean, dark: Boolean) {
        val board = checkNotNull(ZipReplayBoard.givens(envelope))
        composeRule.setContent {
            // currentIsDarkTheme() reads uiMode, so the capture sets it as a device would.
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize()) { HiddenBoard(board, sharp = sharp, modifier = Modifier.padding(Spacing.lg)) }
                }
            }
        }
        composeRule.waitForIdle()
        val name = "hidden-board-${if (sharp) "sharp" else "blurred"}-${if (dark) "dark" else "light"}.png"
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots")
                .resolve(name)
                .canonicalFile
                .also { checkNotNull(it.parentFile).mkdirs() }
                .absolutePath,
        )
    }
}
