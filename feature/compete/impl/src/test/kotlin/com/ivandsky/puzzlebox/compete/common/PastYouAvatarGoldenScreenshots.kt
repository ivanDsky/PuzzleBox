package com.ivandsky.puzzlebox.compete.common

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.resultui.FakePhotos
import com.ivandsky.puzzlebox.resultui.PastYouAvatar
import com.ivandsky.puzzlebox.resultui.PlayerAvatar
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GOLDEN_PHONE = "w411dp-h891dp-420dpi"
private const val SETTLE_MS = 2_000L

/** Past you's avatar at the default 40 dp and the sheet's 48 dp, light and dark. Golden in `snapshots/common/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class PastYouAvatarGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `past you avatar light`() = avatar(dark = false)
    @Test fun `past you avatar dark`() = avatar(dark = true)
    @Test fun `past you photo avatar light`() = photoAvatar(dark = false)
    @Test fun `past you photo avatar dark`() = photoAvatar(dark = true)

    private fun avatar(dark: Boolean) {
        rule.showGolden(dark) {
            Row(
                modifier = Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PastYouAvatar(name = "Ivan")
                PastYouAvatar(name = "Sam", size = 48.dp)
            }
        }
        rule.settleAndCapture("common", "past-you-avatar", "phone", dark)
    }

    /** Past you wearing the player's own photo (ringed, with the mark) beside the plain "you" photo: the two stay apart. */
    private fun photoAvatar(dark: Boolean) {
        rule.showGolden(dark) {
            FakePhotos {
                Row(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerAvatar(name = "Ivan", photoUrl = PHOTO)
                    PastYouAvatar(name = "Ivan", photoUrl = PHOTO)
                    PlayerAvatar(name = "Ivan", photoUrl = PHOTO, size = 48.dp)
                    PastYouAvatar(name = "Ivan", size = 48.dp, photoUrl = PHOTO)
                }
            }
        }
        rule.settleAndCapture("common", "past-you-photo-avatar", "phone", dark)
    }
}

private const val PHOTO = "https://example.test/ivan.jpg"

/**
 * Shows [content] the way the app's root Scaffold does in the [dark] or light theme, system animations off
 * (reduced motion), the compose clock frozen until [settleAndCapture].
 */
private fun ComposeContentTestRule.showGolden(dark: Boolean, content: @Composable () -> Unit) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    mainClock.autoAdvance = false
    setContent {
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(LocalConfiguration provides config) {
            PuzzleBoxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}

/**
 * Lets entrances settle, then captures the whole screen to `snapshots/<group>/<name>-<device>-<light|dark>.png`.
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun ComposeContentTestRule.settleAndCapture(group: String, name: String, device: String, dark: Boolean) {
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/$group/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}
