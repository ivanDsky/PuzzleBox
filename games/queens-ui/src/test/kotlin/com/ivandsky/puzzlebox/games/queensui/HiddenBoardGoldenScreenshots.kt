package com.ivandsky.puzzlebox.games.queensui

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
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of Queens' board before play, on a phone, light and dark: blurred under the veil and its pill, and sharp.
 * The board is built from the puzzle as it travels, solution and all, through [QueensReplayBoard.givens]; the sharp frames show
 * that only what the puzzle gives away is drawn. The capture draws without the GPU, so the blurred frames pin the
 * picture path. Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class HiddenBoardGoldenScreenshots {

    @get:Rule
    val composeRule = createComposeRule()

    // An 8×8 board of uneven regions, two of them one cell, with its solution on board.
    private val puzzle = QueensPuzzle(
        size = 8,
        regions = listOf(
            0, 4, 1, 1, 1, 1, 2, 2,
            4, 4, 1, 2, 1, 1, 2, 2,
            4, 4, 2, 2, 2, 2, 2, 2,
            4, 4, 4, 4, 4, 3, 4, 4,
            5, 5, 4, 5, 4, 4, 4, 5,
            6, 5, 5, 5, 5, 5, 5, 5,
            6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 7, 7, 7, 7, 7, 7,
        ),
        solution = listOf(0, 12, 23, 29, 34, 46, 49, 59).map(::Cell),
    )
    private val envelope = Puzzle(QueensDefinition.id, queensVariantId(8), QueensCodec.VERSION, QueensCodec.encode(puzzle))

    @Test
    fun `blurred`() = capture(sharp = false, dark = false)

    @Test
    fun `blurred, dark`() = capture(sharp = false, dark = true)

    @Test
    fun `sharp`() = capture(sharp = true, dark = false)

    @Test
    fun `sharp, dark`() = capture(sharp = true, dark = true)

    private fun capture(sharp: Boolean, dark: Boolean) {
        val board = checkNotNull(QueensReplayBoard.givens(envelope))
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
