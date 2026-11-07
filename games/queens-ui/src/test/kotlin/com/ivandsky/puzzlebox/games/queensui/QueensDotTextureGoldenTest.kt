package com.ivandsky.puzzlebox.games.queensui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Ticket 20: on 11×11 and 12×12 boards region ids 10 and 11 (K/L) reuse the hues of
 * A/B, so they carry a four-dot texture. Goldens of the densest board (the 03 prototype's
 * 12×12 layout) on a compact phone, light and dark; recorded by default, verified with
 * `-Proborazzi.verify=true`. Same `snapshots/` directory as QueensGoldenScreenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensDotTextureGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `only region ids 10 and up are textured`() {
        assertEquals(
            (0 until 12).map { it >= 10 },
            (0 until 12).map { QueensRegionPalette.textured(it) },
        )
    }

    @Test
    fun `12x12 board draws K and L with the dot texture`() = capture(dark = false, "QueensDotTexture_12x12.png")

    @Test
    fun `12x12 board draws K and L with the dot texture in the dark theme`() = capture(dark = true, "QueensDotTexture_12x12_dark.png")

    private fun capture(dark: Boolean, file: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val n = 12
        val side = queensBoardSideFor(
            paneWidth = 360.dp,
            paneHeight = 640.dp,
            reservedTop = 56.dp,
            reservedBottom = 0.dp,
            cellsPerSide = n,
            maxSide = QueensBoardDefaults.cellMaxSize * n,
        )
        val state = QueensBoardState(
            initialSession = QueensSession(ref = null, puzzle = QueensPuzzle(size = n, regions = LAYOUT_12)),
        )
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    QueensPlayBoard(state, Modifier.size(side))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/$file")
                .canonicalFile
                .absolutePath,
        )
    }

    private companion object {
        // The 03 prototype's 12×12 layout (hex digit = region id; a/b = K/L).
        val LAYOUT_12: List<Int> = listOf(
            "003333333322", "031113355522", "031333355222", "433336335522",
            "444336355222", "446336335252", "446366375552", "446666b75752",
            "846669b777b2", "644469bbbbb2", "64446bbbaab2", "666666b22222",
        ).flatMap { row -> row.map { it.digitToInt(radix = 16) } }
    }
}
