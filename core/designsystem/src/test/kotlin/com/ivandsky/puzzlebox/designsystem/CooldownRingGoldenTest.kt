package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of the cooldown ring half round on a disabled [GameButton], light and dark: the ring
 * hugs the sunk face, concentric with it, and leaves the empty lip strip above it clear.
 * Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class CooldownRingGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `half round on the sunk face of a disabled Hint button`() = capture(dark = false, "CooldownRing_half.png")

    @Test
    fun `half round on the sunk face in the dark theme`() = capture(dark = true, "CooldownRing_half_dark.png")

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
                    Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(Spacing.lg)) {
                        GameButton(
                            onClick = {},
                            icon = PuzzleIcons.Hint,
                            label = "Hint",
                            enabled = false,
                            accent = AccentSlot.Teal,
                            size = GameButtonSize.Tall,
                            modifier = Modifier.cooldownRing({ 0.5f }, AccentSlot.Teal),
                        )
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
}
