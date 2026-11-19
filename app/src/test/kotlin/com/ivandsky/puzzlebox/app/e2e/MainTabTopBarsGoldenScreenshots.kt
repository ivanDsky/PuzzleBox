package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.TopBarFlag
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * MainTabsLargeBar (#1597): Today, Games, Community and Ranks over the real shell on Today's large top bar, on the
 * narrowest phone and the tablet, light and dark. Recorded with `-Proborazzi.record=true` into
 * `snapshots/main-tab-bars/` (untracked review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w360dp-h800dp-420dpi")
class MainTabTopBarsGoldenScreenshotsPhone : MainTabTopBarsGoldenScreenshots(device = "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
class MainTabTopBarsGoldenScreenshotsTablet : MainTabTopBarsGoldenScreenshots(device = "tablet")

abstract class MainTabTopBarsGoldenScreenshots(private val device: String) {

    @Test fun `main tabs light`() = capture(dark = false)
    @Test fun `main tabs dark`() = capture(dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val graph = E2eKoinGraph(E2E_TODAY, module {}, FakeReleaseFlags(TopBarFlag.MainTabsLargeBar))
        shellRobot(graph = graph, darkTheme = dark) {
            for ((key, name) in TABS) {
                tapTab(key)
                rule.waitForIdle()
                captureScreenRoboImage(out(name, dark))
            }
            // The longest board name on the Ranks bar: the title shrinks or ellipsizes, tune and avatar stay whole.
            if (device == "phone") {
                tap("ranks.title")
                rule.onNode(hasText("Mini Sudoku") and hasAnyAncestor(hasTestTag("ranks.menu"))).performClick()
                rule.waitForIdle()
                captureScreenRoboImage(out("ranks-minisudoku", dark))
            }
        }
    }

    private fun out(tab: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/main-tab-bars/$tab-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        val TABS: List<Pair<NavKey, String>> = listOf(HomeKey to "today", PlayKey to "games", CommunityKey to "community", HistoryRootKey to "ranks")
    }
}
