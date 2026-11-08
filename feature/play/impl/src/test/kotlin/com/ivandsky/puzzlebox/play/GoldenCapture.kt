package com.ivandsky.puzzlebox.play

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.io.File

/** Robolectric qualifiers of the two golden devices: a phone, and a landscape tablet. */
internal const val GOLDEN_PHONE = "w411dp-h891dp-420dpi"
internal const val GOLDEN_TABLET = "w1280dp-h800dp-land-mdpi"

/**
 * Shows [content] the way the app's root Scaffold does (background colour, onBackground content
 * colour) in the [dark] or light theme, with system animations off (reduced motion) unless
 * [animated]. While [frozen] the clock only moves through [settleAndCapture]; a screen whose
 * view model loads on the main looper runs unfrozen until it has loaded.
 */
internal fun ComposeContentTestRule.showGolden(
    dark: Boolean,
    frozen: Boolean = true,
    animated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (animated) 1f else 0f)
    mainClock.autoAdvance = !frozen
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
 * Lets entrances settle, then captures the whole screen, dialogs and sheets included, to
 * `snapshots/<group>/<name>-<device>-<light|dark>.png`.
 * Recorded with `-Proborazzi.record=true`, verified with
 * `-Proborazzi.verify=true`.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun ComposeContentTestRule.settleAndCapture(group: String, name: String, device: String, dark: Boolean) {
    mainClock.autoAdvance = false
    mainClock.advanceTimeBy(SETTLE_MS)
    val file = File(checkNotNull(System.getProperty("user.dir")))
        .resolve("snapshots/$group/$name-$device-${if (dark) "dark" else "light"}.png")
        .canonicalFile
        .also { checkNotNull(it.parentFile).mkdirs() }
    captureScreenRoboImage(file.absolutePath)
}

private const val SETTLE_MS = 2_000L
