package com.ivandsky.puzzlebox.resultui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of the result's action stack at the widths a hero card's content gets: 264 dp (a 360 dp phone), 315 dp
 * (a 411 dp phone) and 488 dp (a wide tablet). The primary spans the width; Share and Play again sit side by side as
 * labelled pills at every width. Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultActionsGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val queens = GameId("queens")
    private val maya = ResultPrimaryAction.ChallengeFriend("u1", "Maya")

    @Test
    @Config(qualifiers = PHONE_360)
    fun `360 dp phone, light`() = capture(width = 264.dp, dark = false, "ResultActions_w264.png")

    @Test
    @Config(qualifiers = PHONE_360)
    fun `360 dp phone, dark`() = capture(width = 264.dp, dark = true, "ResultActions_w264_dark.png")

    @Test
    @Config(qualifiers = PHONE_411)
    fun `411 dp phone, light`() = capture(width = 315.dp, dark = false, "ResultActions_w315.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `wide tablet, light`() = capture(width = 488.dp, dark = false, "ResultActions_w488.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `wide tablet, dark`() = capture(width = 488.dp, dark = true, "ResultActions_w488_dark.png")

    private fun capture(width: Dp, dark: Boolean, file: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(Spacing.lg).width(width)) {
                            ResultHeroActions(
                                primary = ResultPrimaryAction.Next(queens, "Queens"),
                                onPrimary = {},
                                onShare = {},
                                onPlayAgain = {},
                                challenge = maya,
                                onChallenge = {},
                            )
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
        const val PHONE_360 = "w360dp-h800dp-420dpi"
        const val PHONE_411 = "w411dp-h800dp-420dpi"
        const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-mdpi"
    }
}
