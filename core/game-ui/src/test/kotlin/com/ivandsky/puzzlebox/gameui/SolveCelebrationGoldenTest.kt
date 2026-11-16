package com.ivandsky.puzzlebox.gameui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of the solve celebration's overlay (L2) on a phone and a landscape tablet, light and dark, over a plain
 * surface: the stamp landed and at rest with no confetti, and the first burst half-way out behind it. They draw
 * [SolveCelebrationFrame] with fixed values, so nothing depends on a clock. Recorded with
 * `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SolveCelebrationGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal)

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, stamp landed`() = capture(burst = null, dark = false, "SolveCelebration_stampLanded_phone.png")

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, stamp landed, dark`() = capture(burst = null, dark = true, "SolveCelebration_stampLanded_phone_dark.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, stamp landed`() =
        capture(burst = null, dark = false, "SolveCelebration_stampLanded_tabletLandscape.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, stamp landed, dark`() =
        capture(burst = null, dark = true, "SolveCelebration_stampLanded_tabletLandscape_dark.png")

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, burst half-way out`() = capture(burst = MID_BURST, dark = false, "SolveCelebration_burst_phone.png")

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, burst half-way out, dark`() = capture(burst = MID_BURST, dark = true, "SolveCelebration_burst_phone_dark.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, burst half-way out`() =
        capture(burst = MID_BURST, dark = false, "SolveCelebration_burst_tabletLandscape.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, burst half-way out, dark`() =
        capture(burst = MID_BURST, dark = true, "SolveCelebration_burst_tabletLandscape_dark.png")

    /** The overlay with the stamp at rest and, when [burst] is not null, the first burst at that progress. */
    private fun capture(burst: Float?, dark: Boolean, file: String) {
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        SolveCelebrationFrame(brand = brand, landed = true, stampScale = { 1f }, stampAlpha = { 1f }) {
                            if (burst != null) {
                                SolveCelebrationBurstFrame(
                                    progress = { burst },
                                    colors = solveCelebrationBurstColors(brand),
                                    seed = 0L,
                                )
                            }
                        }
                    }
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
        const val PHONE = "w360dp-h800dp-420dpi"
        const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-mdpi"

        /** Half-way through the burst's time: the confetti is nearly all the way out and has not begun to fade. */
        const val MID_BURST = 0.5f
    }
}
