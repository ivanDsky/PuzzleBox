package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
 * Goldens of [UnderlineTabPager], light and dark, with page one and with page two selected.
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UnderlineTabPagerGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `page one selected`() = capture("UnderlineTabPager_page_one", dark = false) { TabPagerSample(initialPage = 0) }

    @Test
    fun `page one selected in the dark theme`() = capture("UnderlineTabPager_page_one_dark", dark = true) { TabPagerSample(initialPage = 0) }

    @Test
    fun `page two selected`() = capture("UnderlineTabPager_page_two", dark = false) { TabPagerSample(initialPage = 1) }

    @Test
    fun `page two selected in the dark theme`() = capture("UnderlineTabPager_page_two_dark", dark = true) { TabPagerSample(initialPage = 1) }

    private fun capture(name: String, dark: Boolean, widthDp: Int = 360, fontScale: Float = 1f, content: @Composable () -> Unit) {
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
                PuzzleBoxTheme(darkTheme = dark) {
                    Box(Modifier.width(widthDp.dp).background(MaterialTheme.colorScheme.surface)) { content() }
                }
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
