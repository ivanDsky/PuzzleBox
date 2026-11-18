package com.ivandsky.puzzlebox.app.nav

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.play.api.CommunityKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * M10-1: the four-tab set (Today, Games, Community, Ranks) with Community selected, as the phone's bottom bar and as
 * the tablet's rail, light and dark. Rendered with `-Proborazzi.record=true` into
 * `snapshots/community-tab/` (untracked review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp-420dpi")
class CommunityTabGoldenScreenshotsPhone : CommunityTabGoldenScreenshots(rail = false, device = "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
class CommunityTabGoldenScreenshotsTablet : CommunityTabGoldenScreenshots(rail = true, device = "tablet")

abstract class CommunityTabGoldenScreenshots(private val rail: Boolean, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `community tab selected light`() = capture(dark = false)
    @Test fun `community tab selected dark`() = capture(dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val destinations = topLevelDestinations
        rule.setContent {
            PuzzleBoxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (rail) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            TopLevelNavRail(
                                selected = CommunityKey,
                                onSelect = {},
                                destinations = destinations,
                                bottomActions = { Box(Modifier.size(48.dp)) },
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            TopLevelNavBar(selected = CommunityKey, onSelect = {}, destinations = destinations)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        captureScreenRoboImage(out(dark))
    }

    private fun out(dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/community-tab/community-selected-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath
}
