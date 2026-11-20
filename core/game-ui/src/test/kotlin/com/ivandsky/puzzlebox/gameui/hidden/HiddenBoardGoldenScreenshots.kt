package com.ivandsky.puzzlebox.gameui.hidden

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
 * Goldens of the hidden board over a stand-in board (this module knows no game): blurred and sharp on a phone,
 * light and dark; a wide and a tall board in a tablet pane, where the 400 dp cap shows; and the loading block.
 * The capture draws without the GPU, so these pin the blur of the picture path. Recorded with
 * `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HiddenBoardGoldenScreenshots {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, blurred`() = capture(dark = false, "HiddenBoard_phone.png") { HiddenBoard(sampleGivensBoard(), sharp = false) }

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, blurred, dark`() = capture(dark = true, "HiddenBoard_phone_dark.png") { HiddenBoard(sampleGivensBoard(), sharp = false) }

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, sharp`() = capture(dark = false, "HiddenBoard_phone_sharp.png") { HiddenBoard(sampleGivensBoard(), sharp = true) }

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, sharp, dark`() = capture(dark = true, "HiddenBoard_phone_sharp_dark.png") { HiddenBoard(sampleGivensBoard(), sharp = true) }

    @Test
    @Config(qualifiers = TABLET)
    fun `tablet pane, a wide and a tall board`() = capture(dark = false, "HiddenBoard_tablet_shapes.png") {
        HiddenBoard(sampleGivensBoard(rows = 4, cols = 8), sharp = false)
        HiddenBoard(sampleGivensBoard(rows = 8, cols = 6), sharp = false)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, loading`() = capture(dark = false, "HiddenBoard_phone_loading.png") { HiddenBoardLoading() }

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, loading, dark`() = capture(dark = true, "HiddenBoard_phone_loading_dark.png") { HiddenBoardLoading() }

    private fun capture(dark: Boolean, file: String, content: @Composable () -> Unit) {
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
                        Column(
                            modifier = Modifier.padding(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { content() }
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
        const val PHONE = "w360dp-h640dp-420dpi"
        const val TABLET = "w1280dp-h1000dp-mdpi"
    }
}
