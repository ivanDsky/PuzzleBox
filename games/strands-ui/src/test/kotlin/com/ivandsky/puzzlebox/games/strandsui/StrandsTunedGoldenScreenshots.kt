package com.ivandsky.puzzlebox.games.strandsui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the Strands play screen (nav-rethink B5), light and dark: a phone, a portrait tablet
 * and a landscape tablet, over the real NYT day. The found counter and Hint sit under the board
 * in the shared control row's slots, or in a column beside it on the landscape tablet. Rendered
 * with `-Proborazzi.record=true` into `games/strands-ui/snapshots/` (untracked review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class StrandsTunedGoldenPhoneTest : StrandsTunedGoldenScenes("tuned-phone", PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-mdpi")
class StrandsTunedGoldenTabletPortraitTest : StrandsTunedGoldenScenes("shipped-tablet-portrait", PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StrandsTunedGoldenTabletLandscapeTest : StrandsTunedGoldenScenes("shipped-tablet-landscape", PlayLayout.TwoPane)

abstract class StrandsTunedGoldenScenes(
    private val label: String,
    private val layout: PlayLayout,
) {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(dark: Boolean) {
        // Reduced motion: every animation snaps to its end state, so a capture is a settled frame.
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val controller = StrandsPlayController(
            PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
            now = { Instant.EPOCH },
        ).also { assertEquals(Adopt.Adopted, it.adoptToday(RealDay.fetched.puzzle, RealDay.fetched.ref)) }
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        StrandsPlayScreen(
                            controller = controller,
                            status = PlayBoardStatus.Ready,
                            layout = layout,
                            sidePanel = null,
                            onRetry = {},
                            onSolved = { _, _ -> },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(out(dark))
    }

    private fun out(dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/idle-$label-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    @Test fun `idle light`() = capture(dark = false)
    @Test fun `idle dark`() = capture(dark = true)
}
