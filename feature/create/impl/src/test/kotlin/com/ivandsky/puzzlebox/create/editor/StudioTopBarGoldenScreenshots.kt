package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The Studio's top bar with the autosave indicator in each state: "Saving…" while an edit waits
 * to be stored, "✓ Saved" once it is, with a long game label beside it. The save state keeps its
 * words whole and the subtitle yields. Light and dark, phone and tablet. Goldens in `snapshots/studio/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StudioTopBarGoldenScreenshotsPhone : StudioTopBarGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StudioTopBarGoldenScreenshotsTablet : StudioTopBarGoldenScreenshots("tablet")

abstract class StudioTopBarGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `saving light`() = capture("topbar-saving", saving = true, dark = false)
    @Test fun `saving dark`() = capture("topbar-saving", saving = true, dark = true)

    @Test fun `saved light`() = capture("topbar-saved", saving = false, dark = false)
    @Test fun `saved dark`() = capture("topbar-saved", saving = false, dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, saving: Boolean, dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TopBar(saving) }
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        captureScreenRoboImage(out(name, dark))
    }

    @Composable
    private fun TopBar(saving: Boolean) {
        Column(Modifier.fillMaxWidth()) {
            StudioTopBar(
                title = "Castle of the Glass Orchard",
                subtitle = "Queens 7×7 · Unique",
                saving = saving,
                onBack = {},
                onRename = {},
                onDuplicate = {},
                onDelete = {},
                pill = {},
            )
        }
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/studio/$name-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
