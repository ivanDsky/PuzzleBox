package com.ivandsky.puzzlebox.games.queensui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Queens board takes its fills from the APP theme ([PuzzleBoxTheme]'s `darkTheme`),
 * not the device night flag: Settings > Appearance can force Light on a night-mode
 * device and Dark on a day-mode one. The device flag is set OPPOSITE the theme here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensAppThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Empty fixture board: its regions are its rows, so cell 0 is bare region 0 fill. */
    private val state = QueensBoardState(initialSession = QueensUiFixtures.session())

    private fun cellZeroCentre(themeDark: Boolean, deviceNight: Boolean): Color {
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (deviceNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                PuzzleBoxTheme(darkTheme = themeDark) {
                    Box(Modifier.size(320.dp)) {
                        QueensPlayBoard(state, Modifier.size(320.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val image = composeRule.onRoot().captureToImage()
        val px = IntArray(image.width * image.height)
        image.readPixels(px)
        // Cell 0 is the top-left of a 4x4 board: its centre sits at 1/8 of the board side.
        val x = image.width / 8
        val y = image.height / 8
        return Color(px[y * image.width + x])
    }

    @Test
    fun `light app theme on a night device paints the light fill`() {
        val actual = cellZeroCentre(themeDark = false, deviceNight = true)
        assertEquals(
            QueensRegionPalette.fill(0, dark = false).toArgb().toUInt().toString(16),
            actual.toArgb().toUInt().toString(16),
        )
    }

    @Test
    fun `dark app theme on a day device paints the dark fill`() {
        val actual = cellZeroCentre(themeDark = true, deviceNight = false)
        assertEquals(
            QueensRegionPalette.fill(0, dark = true).toArgb().toUInt().toString(16),
            actual.toArgb().toUInt().toString(16),
        )
    }
}
