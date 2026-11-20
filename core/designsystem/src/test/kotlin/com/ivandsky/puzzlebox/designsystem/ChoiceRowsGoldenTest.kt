package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
 * Review captures of [ChoiceRows] and [ChoiceMessage]: every state stacked in one image at phone (412dp) and tablet (840dp)
 * width, light and dark, plus the longest labels at font scale 1.3. Rendered with
 * `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h2000dp-420dpi")
class ChoiceRowsGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Every state stacked, in the order of the ticket: chosen, with icons, locked option, group disabled, error, longest. */
    @Composable
    private fun EveryState() {
        Column {
            ChoiceRowsChosenPreview()
            ChoiceRowsIconsPreview()
            ChoiceRowsLockedOptionPreview()
            ChoiceRowsGroupDisabledPreview()
            ChoiceRowsErrorPreview()
            ChoiceRowsLongestLabelsPreview()
        }
    }

    @Test
    fun `every state on a phone in the light theme`() = capture("ChoiceRows_phone", widthDp = 412, dark = false) { EveryState() }

    @Test
    fun `every state on a phone in the dark theme`() = capture("ChoiceRows_phone_dark", widthDp = 412, dark = true) { EveryState() }

    @Test
    fun `every state on a tablet in the light theme`() = capture("ChoiceRows_tablet", widthDp = 840, dark = false) { EveryState() }

    @Test
    fun `every state on a tablet in the dark theme`() = capture("ChoiceRows_tablet_dark", widthDp = 840, dark = true) { EveryState() }

    @Test
    fun `the longest labels at font scale 1 point 3 in the light theme`() =
        capture("ChoiceRows_longest_font13", widthDp = 412, dark = false, fontScale = 1.3f) { ChoiceRowsLongestLabelsPreview() }

    @Test
    fun `the longest labels at font scale 1 point 3 in the dark theme`() =
        capture("ChoiceRows_longest_font13_dark", widthDp = 412, dark = true, fontScale = 1.3f) { ChoiceRowsLongestLabelsPreview() }

    private fun capture(name: String, widthDp: Int, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                Box(Modifier.width(widthDp.dp)) { content() }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/$name.png")
                .canonicalFile
                .absolutePath,
        )
    }
}
