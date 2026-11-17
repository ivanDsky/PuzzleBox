package com.ivandsky.puzzlebox.gameui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * Goldens of the result's action stack: the full-width primary, then Share and Play again as equal labelled pills,
 * then the challenge, on a phone and a landscape tablet, light and dark. Recorded with `-Proborazzi.record=true`,
 * verified with `-Proborazzi.verify=true`.
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
    @Config(qualifiers = PHONE)
    fun `phone, light`() = capture(dark = false, "ResultActions_phone.png")

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, dark`() = capture(dark = true, "ResultActions_phone_dark.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, light`() = capture(dark = false, "ResultActions_tabletLandscape.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, dark`() = capture(dark = true, "ResultActions_tabletLandscape_dark.png")

    private fun capture(dark: Boolean, file: String) {
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
                        Column(Modifier.padding(Spacing.lg).widthIn(max = 420.dp)) {
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
        const val PHONE = "w360dp-h800dp-420dpi"
        const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-mdpi"
    }
}
