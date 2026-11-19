package com.ivandsky.puzzlebox.resultui.replay

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of the replay's "Errors" switch in both states, light and dark, on the page background it sits on, beside
 * the page's own padding. Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h200dp-420dpi")
class ShowErrorsToggleGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `light, errors shown`() = capture(showErrors = true, dark = false, "ShowErrors_on.png")

    @Test
    fun `light, errors hidden`() = capture(showErrors = false, dark = false, "ShowErrors_off.png")

    @Test
    fun `dark, errors shown`() = capture(showErrors = true, dark = true, "ShowErrors_on_dark.png")

    private fun capture(showErrors: Boolean, dark: Boolean, file: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = dark) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(Spacing.lg),
                ) {
                    ShowErrorsToggle(showErrors = showErrors, onChange = {})
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
